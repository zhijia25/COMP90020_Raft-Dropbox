package raft;

import raft.common.NodeConfig;
import raft.entity.AentryParam;
import raft.entity.AentryResult;
import raft.entity.RvoteParam;
import raft.entity.RvoteResult;
import raft.client.ClientKVAck;
import raft.client.ClientKVReq;


public interface Node<T> extends LifeCycle{

    /**
     * configration
     *
     * @param config
     */
    void setConfig(NodeConfig config);

    /**
     * handle voting request
     *
     * @param param
     * @return
     */
    RvoteResult handlerRequestVote(RvoteParam param);

    /**
     * handle appending log request
     *
     * @param param
     * @return
     */
    AentryResult handlerAppendEntries(AentryParam param);

    /**
     * handle client request
     *
     * @param request
     * @return
     */
    ClientKVAck handlerClientRequest(ClientKVReq request);

    /**
     * forward to leader node
     * @param request
     * @return
     */
    ClientKVAck redirect(ClientKVReq request);

}
