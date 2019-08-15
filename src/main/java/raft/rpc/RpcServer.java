package raft.rpc;

/**
 * 
 */
public interface RpcServer {

    void start();

    void stop();

    Response handlerRequest(Request request);

}
