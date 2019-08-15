package raft.rpc;

/**
 *
 * 
 */
public interface RpcClient {

    Response send(Request request);

	void stop();

}
