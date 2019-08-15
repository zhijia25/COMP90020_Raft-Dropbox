package raft.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import raft.common.NodeStatus;
import raft.common.Peer;
import raft.entity.LogEntry;
import raft.membership.changes.ClusterMembershipChanges;
import raft.membership.changes.Result;
import raft.rpc.Request;
import raft.rpc.Response;

/**
 *
 * port changes for cluster configuration.
 *
 * 
 */
public class ClusterMembershipChangesImpl implements ClusterMembershipChanges {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClusterMembershipChangesImpl.class);


    private final DefaultNode node;

    public ClusterMembershipChangesImpl(DefaultNode node) {
        this.node = node;
    }

    /** have to be synchronized,one node added one time
     * @param newPeer*/
    @Override
    public synchronized Result addPeer(Peer newPeer) {
        // existed
        if (node.peerSet.getPeersWithOutSelf().contains(newPeer)) {
            return new Result();
        }

        node.peerSet.getPeersWithOutSelf().add(newPeer);

        if (node.status == NodeStatus.LEADER) {
            node.nextIndexs.put(newPeer, 0L);
            node.matchIndexs.put(newPeer, 0L);

            for (long i = 0; i < node.logModule.getLastIndex(); i++) {
                LogEntry e = node.logModule.read(i);
                if (e != null) {
                    node.replication(newPeer, e);
                }
            }

            for (Peer item : node.peerSet.getPeersWithOutSelf()) {
                // TODO synchronized to other nodes
                Request request = Request.newBuilder()
                    .cmd(Request.CHANGE_CONFIG_ADD)
                    .url(newPeer.getAddr())
                    .obj(newPeer)
                    .build();

                Response response = node.rpcClient.send(request);
                Result result = (Result) response.getResult();
                if (result != null && result.getStatus() == Result.Status.SUCCESS.getCode()) {
                    LOGGER.info("replication config success, peer : {}, newServer : {}", newPeer, newPeer);
                } else {
                    LOGGER.warn("replication config fail, peer : {}, newServer : {}", newPeer, newPeer);
                }
            }

        }

        return new Result();
    }


    /** have to be synchronized,one node deleted one time
     * @param oldPeer*/
    @Override
    public synchronized Result removePeer(Peer oldPeer) {
        node.peerSet.getPeersWithOutSelf().remove(oldPeer);
        node.nextIndexs.remove(oldPeer);
        node.matchIndexs.remove(oldPeer);

        return new Result();
    }
}
