package raft.rpc;

import com.alipay.remoting.exception.RemotingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import raft.exception.RaftRemotingException;

/**
 *
 * 
 */
public class DefaultRpcClient implements RpcClient {

    public static Logger logger = LoggerFactory
        .getLogger(DefaultRpcClient.class.getName());

    private static com.alipay.remoting.rpc.RpcClient CLIENT = new com.alipay.remoting.rpc.RpcClient();
    static {
        CLIENT.init();
    }


    @Override
    public Response send(Request request) {
        Response result = null;
        try {
            result = (Response) CLIENT.invokeSync(request.getUrl(), request, 200000);
        } catch (RemotingException e) {
            //e.printStackTrace();
            logger.info("rpc RaftRemotingException ");
            throw new RaftRemotingException();
        } catch (InterruptedException e) {
            //e.printStackTrace();
        } catch (Exception e) {
        	
        }
        return (result);
    }
    

    @Override
    public void stop() {
        CLIENT.shutdown();
    }
}
