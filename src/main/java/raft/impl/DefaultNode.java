package raft.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import raft.Consensus;
import raft.LifeCycle;
import raft.LogModule;
import raft.Node;
import raft.StateMachine;
import raft.common.NodeConfig;
import raft.common.NodeStatus;
import raft.common.Peer;
import raft.common.PeerSet;
import raft.current.RaftThreadPool;
import raft.entity.AentryParam;
import raft.entity.AentryResult;
import raft.entity.Command;
import raft.entity.LogEntry;
import raft.entity.ReplicationFailModel;
import raft.entity.RvoteParam;
import raft.entity.RvoteResult;
import raft.exception.RaftRemotingException;
import raft.membership.changes.ClusterMembershipChanges;
import raft.membership.changes.Result;
import raft.rpc.DefaultRpcClient;
import raft.rpc.DefaultRpcServer;
import raft.rpc.Request;
import raft.rpc.Response;
import raft.rpc.RpcClient;
import raft.rpc.RpcServer;
import raft.util.LongConvert;
import lombok.Getter;
import lombok.Setter;
import raft.client.ClientKVAck;
import raft.client.ClientKVReq;

import static raft.common.NodeStatus.LEADER;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * abstract machine node, default to be follower, the role changes timely
 * 
 */
@Getter
@Setter
public class DefaultNode<T> implements Node<T>, LifeCycle, ClusterMembershipChanges {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNode.class);

    /** gap between voting */
    public volatile long electionTime = 15 * 1000;
    /** last voting time */
    public volatile long preElectionTime = 0;

    /** last heart beat timestamp */
    public volatile long preHeartBeatTime = 0;
    /** gap between heart beat*/
    public final long heartBeatTick = 5 * 1000;


    private HeartBeatTask heartBeatTask = new HeartBeatTask();
    private ElectionTask electionTask = new ElectionTask();
    private ReplicationFailQueueConsumer replicationFailQueueConsumer = new ReplicationFailQueueConsumer();

    private LinkedBlockingQueue<ReplicationFailModel> replicationFailQueue = new LinkedBlockingQueue<>(2048);


    /**
     * node present status
     * @see NodeStatus
     */
    public volatile int status = NodeStatus.FOLLOWER;

    public PeerSet peerSet;



    /* ============ long lasting on every server ============= */

    /** last time that servers know the therm, default by one, keep increasing */
    volatile long currentTerm = 0;
    /** the id of candidate who get voted*/
    volatile String votedFor;
    /** log item set；each item contains a instruction of the execution of user state machine and a term of receiving */
    LogModule logModule;



    /* ============ always change on every server ============= */

    /** maximum index of log item which is known to be submitted*/
    volatile long commitIndex;

    /** the index of state machine which is last to be used(default to be 0, keep increasing) */
    volatile long lastApplied = 0;

    /* ========== always change at leader(reset after voting) ================== */

    /** for each server, send it the index of next log item(default to be 1+the last index of leader) */
    Map<Peer, Long> nextIndexs;

    /** the maximum index of logs which are already copied to each server*/
    Map<Peer, Long> matchIndexs;



    /* ============================== */

    public volatile boolean started;

    public NodeConfig config;

    public static RpcServer RPC_SERVER;

    public RpcClient rpcClient = new DefaultRpcClient();

    public StateMachine stateMachine;

    /* ============================== */

    /** achievement of consistency module */
    Consensus consensus;

    ClusterMembershipChanges delegate;


    /* ============================== */

    private DefaultNode() {
    }

    public static DefaultNode getInstance() {
        return DefaultNodeLazyHolder.INSTANCE;
    }


    private static class DefaultNodeLazyHolder {

        private static final DefaultNode INSTANCE = new DefaultNode();
    }

    @Override
    public void init() throws Throwable {
        if (started) {
            return;
        }
        synchronized (this) {
            if (started) {
                return;
            }
            RPC_SERVER.start();

            consensus = new DefaultConsensus(this);
            delegate = new ClusterMembershipChangesImpl(this);

            RaftThreadPool.scheduleWithFixedDelay(heartBeatTask, 500);
            RaftThreadPool.scheduleAtFixedRate(electionTask, 6000, 500);
            RaftThreadPool.execute(replicationFailQueueConsumer);

            LogEntry logEntry = logModule.getLast();
            if (logEntry != null) {
                currentTerm = logEntry.getTerm();
            }

            started = true;

            LOGGER.info("start success, selfId : {} ", peerSet.getSelf());
        }
    }

    @Override
    public void setConfig(NodeConfig config) {
        this.config = config;
        stateMachine = DefaultStateMachine.getInstance();
        logModule = DefaultLogModule.getInstance();

        peerSet = PeerSet.getInstance();
        for (String s : config.getPeerAddrs()) {
            Peer peer = new Peer(s);
            peerSet.addPeer(peer);
            if (s.equals("localhost:" + config.getSelfPort())) {
                peerSet.setSelf(peer);
            }
        }

        RPC_SERVER = new DefaultRpcServer(config.selfPort, this);
    }


    @Override
    public RvoteResult handlerRequestVote(RvoteParam param) {
        LOGGER.warn("handlerRequestVote will be invoke, param info : {}", param);
        return consensus.requestVote(param);
    }

    @Override
    public AentryResult handlerAppendEntries(AentryParam param) {
        if (param.getEntries() != null) {
            LOGGER.warn("node receive node {} append entry, entry content = {}", param.getLeaderId(), param.getEntries());
        }

        return consensus.appendEntries(param);

    }


    @SuppressWarnings("unchecked")
    @Override
    public ClientKVAck redirect(ClientKVReq request) {
        Request<ClientKVReq> r = Request.newBuilder().
            obj(request).url(peerSet.getLeader().getAddr()).cmd(Request.CLIENT_REQ).build();
        Response response = rpcClient.send(r);
        return (ClientKVAck) response.getResult();
    }

    /**
     * each request of server contains a instrcution which is executed by copy state machine
     * leader add this instruction as a new item in the log, then paralelly sending item to other servers by RPC, let them copy this item
     * when this item is copied correctly,thye leader will apply this item into state machine and return it to the client
     * if follower crashes or runs slowly, or package lost on channel
     * the leader will keep attenmpting to add item by RPC(even though reply to the server) until all followers stored all the items
     * @param request
     * @return
     */
    @Override
    public synchronized ClientKVAck handlerClientRequest(ClientKVReq request) {

        LOGGER.warn("handlerClientRequest handler {} operation,  and key : [{}], value : [{}]",
            ClientKVReq.Type.value(request.getType()), request.getKey(), request.getValue());

        if (status != LEADER) {
            LOGGER.warn("I not am leader , only invoke redirect method, leader addr : {}, my addr : {}",
                peerSet.getLeader(), peerSet.getSelf().getAddr());
            return redirect(request);
        }

        if (request.getType() == ClientKVReq.GET) {
            LogEntry logEntry = stateMachine.get(request.getKey());
            if (logEntry != null) {
                return new ClientKVAck(logEntry.getCommand());
            }
            return new ClientKVAck(null);
        }

        LogEntry logEntry = LogEntry.newBuilder()
            .command(Command.newBuilder().
                key(request.getKey()).
                value(request.getValue()).
                build())
            .term(currentTerm)
            .build();

        // pre-submit to local log
        logModule.write(logEntry);
        LOGGER.info("write logModule success, logEntry info : {}, log index : {}", logEntry, logEntry.getIndex());

        final AtomicInteger success = new AtomicInteger(0);

        List<Future<Boolean>> futureList = new CopyOnWriteArrayList<>();

        int count = 0;
        //  copy to other machines
        for (Peer peer : peerSet.getPeersWithOutSelf()) {
            // TODO check self and RaftThreadPool
            count++;
            // prralley copy by RPC
            futureList.add(replication(peer, logEntry));
        }

        CountDownLatch latch = new CountDownLatch(futureList.size());
        List<Boolean> resultList = new CopyOnWriteArrayList<>();

        getRPCAppendResult(futureList, latch, resultList);

        try {
            latch.await(4000, MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (Boolean aBoolean : resultList) {
            if (aBoolean) {
                success.incrementAndGet();
            }
        }

        // if there is N<commitIndex, and most matchIndex[i] ≥ N
        // and log[N].term == currentTerm，then commitIndex = N 
        List<Long> matchIndexList = new ArrayList<>(matchIndexs.values());
        // < 2, useless
        int median = 0;
        if (matchIndexList.size() >= 2) {
            Collections.sort(matchIndexList);
            median = matchIndexList.size() / 2;
        }
        Long N = matchIndexList.get(median);
        if (N > commitIndex) {
            LogEntry entry = logModule.read(N);
            if (entry != null && entry.getTerm() == currentTerm) {
                commitIndex = N;
            }
        }

        //  response to the client
        if (success.get() >= (count / 2)) {
            // update
            commitIndex = logEntry.getIndex();
            //  apply to state machine
            getStateMachine().apply(logEntry);
            lastApplied = commitIndex;

            LOGGER.info("success apply local state machine,  logEntry info : {}", logEntry);
            // successfully return
            return ClientKVAck.ok();
        } else {
            logModule.removeOnStartIndex(logEntry.getIndex());
            LOGGER.warn("fail apply local state  machine,  logEntry info : {}", logEntry);
            // not applied to state machine, but reseved in the log, retrived by timely tasks from the retrying queue, keep attempting, when the condition is meeted, apply to the state machine
            // return False since not successfully copy to more than half of machines
            return ClientKVAck.fail();
        }
    }

    private void getRPCAppendResult(List<Future<Boolean>> futureList, CountDownLatch latch, List<Boolean> resultList) {
        for (Future<Boolean> future : futureList) {
            RaftThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        resultList.add(future.get(3000, MILLISECONDS));
                    } catch (CancellationException | TimeoutException | ExecutionException | InterruptedException e) {
                        //e.printStackTrace();//asdadasdasdsadadasdadasdadasdadasdaassssssssssssssssssssssssssssssssssssssssssssssssssssss
                        resultList.add(false);
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }
    }


    /** copy to other machines  */
    public Future<Boolean> replication(Peer peer, LogEntry entry) {

        return RaftThreadPool.submit(new Callable() {
            @Override
            public Boolean call() throws Exception {

                long start = System.currentTimeMillis(), end = start;

                // 20s retry time
                while (end - start < 20 * 1000L) {

                    AentryParam aentryParam = new AentryParam();
                    aentryParam.setTerm(currentTerm);
                    aentryParam.setServerId(peer.getAddr());
                    aentryParam.setLeaderId(peerSet.getSelf().getAddr());

                    aentryParam.setLeaderCommit(commitIndex);

                    // this action isn't meaningful until the new leader's first RPC
                    Long nextIndex = nextIndexs.get(peer);
                    LinkedList<LogEntry> logEntries = new LinkedList<>();
                    if (entry.getIndex() >= nextIndex) {
                        for (long i = nextIndex; i <= entry.getIndex(); i++) {
                            LogEntry l = logModule.read(i);
                            if (l != null) {
                                logEntries.add(l);
                            }
                        }
                    } else {
                        logEntries.add(entry);
                    }
                    // minimun log
                    LogEntry preLog = getPreLog(logEntries.getFirst());
                    aentryParam.setPreLogTerm(preLog.getTerm());
                    aentryParam.setPrevLogIndex(preLog.getIndex());

                    aentryParam.setEntries(logEntries.toArray(new LogEntry[0]));

                    Request request = Request.newBuilder()
                        .cmd(Request.A_ENTRIES)
                        .obj(aentryParam)
                        .url(peer.getAddr())
                        .build();

                    try {
                        Response response = getRpcClient().send(request);
                        if (response == null) {
                            return false;
                        }
                        AentryResult result = (AentryResult) response.getResult();
                        if (result != null && result.isSuccess()) {
                            LOGGER.info("append follower entry success , follower=[{}], entry=[{}]", peer, aentryParam.getEntries());
                            // update the two tracing value
                            nextIndexs.put(peer, entry.getIndex() + 1);
                            matchIndexs.put(peer, entry.getIndex());
                            return true;
                        } else if (result != null) {
                            if (result.getTerm() > currentTerm) {
                                LOGGER.warn("follower [{}] term [{}] than more self, and my term = [{}], so, I will become follower",
                                    peer, result.getTerm(), currentTerm);
                                currentTerm = result.getTerm();
                                // give up, to be follower
                                status = NodeStatus.FOLLOWER;
                                return false;
                            } // smaller but fail, means wrong index or term
                            else {
                                // decrease
                                if (nextIndex == 0) {
                                    nextIndex = 1L;
                                }
                                nextIndexs.put(peer, nextIndex - 1);
                                LOGGER.warn("follower {} nextIndex not match, will reduce nextIndex and retry RPC append, nextIndex : [{}]", peer.getAddr(),
                                    nextIndex);
                                // retry, until success
                            }
                        }

                        end = System.currentTimeMillis();

                    } catch (Exception e) {
                        LOGGER.warn(e.getMessage(), e);
//                        ReplicationFailModel model =  ReplicationFailModel.newBuilder()
//                            .callable(this)
//                            .logEntry(entry)
//                            .peer(peer)
//                            .offerTime(System.currentTimeMillis())
//                            .build();
//                        replicationFailQueue.offer(model);
                        return false;
                    }
                }
                return false;
            }
        });

    }

    private LogEntry getPreLog(LogEntry logEntry) {
        LogEntry entry = logModule.read(logEntry.getIndex() - 1);

        if (entry == null) {
            LOGGER.warn("get perLog is null , parameter logEntry : {}", logEntry);
            entry = LogEntry.newBuilder().index(0L).term(0).command(null).build();
        }
        return entry;
    }


    class ReplicationFailQueueConsumer implements Runnable {

        /** 1 min */
        long intervalTime = 1000 * 60;

        @Override
        public void run() {
            for (; ; ) {

                try {
                    ReplicationFailModel model = replicationFailQueue.take();
                    if (status != LEADER) {
                        replicationFailQueue.clear();
                        continue;
                    }
                    LOGGER.warn("replication Fail Queue Consumer take a task, will be retry replication, content detail : [{}]", model.logEntry);
                    long offerTime = model.offerTime;
                    if (System.currentTimeMillis() - offerTime > intervalTime) {
                        LOGGER.warn("replication Fail event Queue maybe full or handler slow");
                    }

                    Callable callable = model.callable;
                    Future<Boolean> future = RaftThreadPool.submit(callable);
                    Boolean r = future.get(3000, MILLISECONDS);
                    // retry success
                    if (r) {
                        // may be qulified to be state machine
                        tryApplyStateMachine(model);
                    }

                } catch (InterruptedException e) {
                    // ignore
                } catch (ExecutionException | TimeoutException e) {
                    LOGGER.warn(e.getMessage());
                }
            }
        }
    }

    private void tryApplyStateMachine(ReplicationFailModel model) {

        String success = stateMachine.getString(model.successKey);
        stateMachine.setString(model.successKey, String.valueOf(Integer.valueOf(success) + 1));

        String count = stateMachine.getString(model.countKey);

        if (Integer.valueOf(success) >= Integer.valueOf(count) / 2) {
            stateMachine.apply(model.logEntry);
            stateMachine.delString(model.countKey, model.successKey);
        }
    }


    @Override
    public void destroy() throws Throwable {
        RPC_SERVER.stop();
    }


    /**
     * 1. start voting process once turns to be candidate在
     *      increasing present currentTerm
     *      vote for itself
     *      reset timer of voting timeout
     *      send RPC request to each server by RPC
     * 2. become leader if get most votes
     * 3. if receive the appending log of new leader, turns to be follower
     * 4. if voting timeout, retry a new voting round
     */
    class ElectionTask implements Runnable {

        @Override
        public void run() {

            if (status == LEADER) {
                return;
            }

            long current = System.currentTimeMillis();
            // handle conflict based on RATF random time
            electionTime = electionTime + ThreadLocalRandom.current().nextInt(50);
            if (current - preElectionTime < electionTime) {
                return;
            }
            status = NodeStatus.CANDIDATE;
            LOGGER.error("node {} will become CANDIDATE and start election leader, current term : [{}], LastEntry : [{}]",
                peerSet.getSelf(), currentTerm, logModule.getLast());

            preElectionTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(200) + 150;

            currentTerm = currentTerm + 1;
            // elect itself
            votedFor = peerSet.getSelf().getAddr();

            List<Peer> peers = peerSet.getPeersWithOutSelf();

            ArrayList<Future> futureArrayList = new ArrayList<>();

            LOGGER.info("peerList size : {}, peer list content : {}", peers.size(), peers);

            // send request
            for (Peer peer : peers) {

                futureArrayList.add(RaftThreadPool.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        long lastTerm = 0L;
                        LogEntry last = logModule.getLast();
                        if (last != null) {
                            lastTerm = last.getTerm();
                        }

                        RvoteParam param = RvoteParam.newBuilder().
                            term(currentTerm).
                            candidateId(peerSet.getSelf().getAddr()).
                            lastLogIndex(LongConvert.convert(logModule.getLastIndex())).
                            lastLogTerm(lastTerm).
                            build();

                        Request request = Request.newBuilder()
                            .cmd(Request.R_VOTE)
                            .obj(param)
                            .url(peer.getAddr())
                            .build();

                        try {
                            @SuppressWarnings("unchecked")
                            Response<RvoteResult> response = getRpcClient().send(request);
                            return response;

                        } catch (RaftRemotingException e) {
                            LOGGER.error("ElectionTask RPC Fail , URL : " + request.getUrl());
                            return null;
                        }
                    }
                }));
            }

            AtomicInteger success2 = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(futureArrayList.size());

            LOGGER.info("futureArrayList.size() : {}", futureArrayList.size());
            // wait for result
            for (Future future : futureArrayList) {
                RaftThreadPool.submit(new Callable() {
                    @Override
                    public Object call() throws Exception {
                        try {

                            @SuppressWarnings("unchecked")
                            Response<RvoteResult> response = (Response<RvoteResult>) future.get(3000, MILLISECONDS);
                            if (response == null) {
                                return -1;
                            }
                            boolean isVoteGranted = response.getResult().isVoteGranted();

                            if (isVoteGranted) {
                                success2.incrementAndGet();
                            } else {
                                // update its term
                                long resTerm = response.getResult().getTerm();
                                if (resTerm >= currentTerm) {
                                    currentTerm = resTerm;
                                }
                            }
                            return 0;
                        } catch (Exception e) {
                            LOGGER.error("future.get exception , e : ", e);
                            return -1;
                        } finally {
                            latch.countDown();
                        }
                    }
                });
            }

            try {
                // wait for a while
                latch.await(3500, MILLISECONDS);
            } catch (InterruptedException e) {
                LOGGER.warn("InterruptedException By Master election Task");
            }

            int success = success2.get();
            LOGGER.info("node {} maybe become leader , success count = {} , status : {}", peerSet.getSelf(), success, NodeStatus.Enum.value(status));
            // if other server send appendEntry while voting, then mignt be follower, should stop
            if (status == NodeStatus.FOLLOWER) {
                return;
            }
            // plus itself
            if (success >= peers.size() / 2) {
                LOGGER.warn("node {} become leader ", peerSet.getSelf());
                status = LEADER;
                peerSet.setLeader(peerSet.getSelf());
                votedFor = "";
                becomeLeaderToDoThing();
            } else {
                // else re-elect
                votedFor = "";
            }

        }
    }

    /**
     * initialize all nextIndex as q+ index of its last log, if leader different from next RPC, then fail
     * then leader decrese the nextIndex by 1 and retry till consistency
     */
    private void becomeLeaderToDoThing() {
        nextIndexs = new ConcurrentHashMap<>();
        matchIndexs = new ConcurrentHashMap<>();
        for (Peer peer : peerSet.getPeersWithOutSelf()) {
            nextIndexs.put(peer, logModule.getLastIndex() + 1);
            matchIndexs.put(peer, 0L);
        }
    }


    class HeartBeatTask implements Runnable {

        @Override
        public void run() {

            if (status != LEADER) {
                return;
            }

            long current = System.currentTimeMillis();
            if (current - preHeartBeatTime < heartBeatTick) {
                return;
            }
            LOGGER.info("=========== NextIndex =============");
            for (Peer peer : peerSet.getPeersWithOutSelf()) {
                LOGGER.info("Peer {} nextIndex={}", peer.getAddr(), nextIndexs.get(peer));
            }

            preHeartBeatTime = System.currentTimeMillis();

            // only term and leaderID matters for heart beat
            for (Peer peer : peerSet.getPeersWithOutSelf()) {

                AentryParam param = AentryParam.newBuilder()
                    .entries(null)// heart beat, empty 
                    .leaderId(peerSet.getSelf().getAddr())
                    .serverId(peer.getAddr())
                    .term(currentTerm)
                    .build();

                Request<AentryParam> request = new Request<>(
                    Request.A_ENTRIES,
                    param,
                    peer.getAddr());

                RaftThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Response response = getRpcClient().send(request);
                            AentryResult aentryResult = (AentryResult) response.getResult();
                            long term = aentryResult.getTerm();

                            if (term > currentTerm) {
                                LOGGER.error("self will become follower, he's term : {}, my term : {}", term, currentTerm);
                                currentTerm = term;
                                votedFor = "";
                                status = NodeStatus.FOLLOWER;
                            }
                        } catch (Exception e) {
                            LOGGER.error("HeartBeatTask RPC Fail, request URL : {} ", request.getUrl());
                        }
                    }
                }, false);
            }
        }
    }

    @Override
    public Result addPeer(Peer newPeer) {
        return delegate.addPeer(newPeer);
    }

    @Override
    public Result removePeer(Peer oldPeer) {
        return delegate.removePeer(oldPeer);
    }

}
