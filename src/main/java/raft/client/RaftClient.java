package raft.client;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

//import com.alibaba.fastjson.JSONObject;
import com.alipay.remoting.exception.RemotingException;
import com.google.common.collect.Lists;

import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import raft.current.SleepHelper;
import raft.entity.LogEntry;
import raft.rpc.DefaultRpcClient;
import raft.rpc.Request;
import raft.rpc.Response;
import raft.rpc.RpcClient;

/**
 *
 * 
 */

public class RaftClient {
	

    private static final Logger LOGGER = LoggerFactory.getLogger(RaftClient.class);
    private static int logIndex;

    private final static RpcClient client = new DefaultRpcClient();
    private final static ClientOperation op = new ClientOperation();

    static String addr = "localhost:8778";
    static List<String> list = Lists.newArrayList("localhost:8775","localhost:8776","localhost:8777", "localhost:8778", "localhost:8779");

    public static void main(String[] args) throws RemotingException, InterruptedException {
    	
    	try {
			
	           
			Scanner sc = new Scanner(System.in);					
			while (true) {
			System.out.println("please select your starting mode, 1 for synchronize, 2 for reset");
			String answer = sc.nextLine();
	        if (answer.equals("1")){
	        	op.reset();
	        	syn_request();
	        	System.out.println("you choose to synchronize your folder");
	        	String path = "D:/test";
		        ListenerThread fileListener = new ListenerThread(path);
		        fileListener.addListener(path);	
	        	break;
	        }
	        else if (answer.equals("2")) {
	        	op.reset();
	        	reset_request();
	        	System.out.println("you choose to reset your folder");
	        	String path = "D:/test";
		        ListenerThread fileListener = new ListenerThread(path);
		        fileListener.addListener(path);	
	        	break;
	        }
	        else {
	        	System.out.println("try again, please just enter 1 or 2");
	        }}
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    public static void request(JSONObject req) throws RemotingException, InterruptedException {
		AtomicLong count = new AtomicLong(5);
		int next_index = 1;
		if (((String) req.get("value")).equals("syn"))
			syn_request();
		else if(((String) req.get("value")).equals("reset"))
			reset_request();
		else
		{
			for (int i = 3; ; i++) {
	            try {
	                int index = (int) (count.incrementAndGet() % list.size());
	                addr = list.get(index);

	                ClientKVReq obj = ClientKVReq.newBuilder().key(""+logIndex).value((String)req.get("value")).type(ClientKVReq.PUT).build();

	                Request<ClientKVReq> r = new Request<>();
	                r.setObj(obj);
	                r.setUrl(addr);
	                r.setCmd(Request.CLIENT_REQ);
	                Response<String> response;
	                try {
	                    response = client.send(r);
	                } catch (Exception e) {
	                    r.setUrl(list.get((int) ((count.incrementAndGet()) % list.size())));
	                    response = client.send(r);
	                }
	                if(response.toString().equals("Response{result=ClientKVAck(result=ok)}")) {
	                	logIndex++;
	                	LOGGER.info("request content : {}, url : {}, put response : {}", obj.key + "=" + obj.getValue(), r.getUrl(), response.getResult());
	                	//client.stop();
	                	break;
	                }
	                
	                

	                SleepHelper.sleep(1000);
	            } catch (Exception e) {
	                e.printStackTrace();
	                i = i - 1;
	            }

	            SleepHelper.sleep(5000);
	            System.exit(0);
	        }
		}
        
	}
    
    
    public static int syn_request() throws RemotingException, InterruptedException {
		AtomicLong count = new AtomicLong(5);
		int ii = 0;
		for (int i = 3; ; i++) {
            try {
                int index = (int) (count.incrementAndGet() % list.size());

                Request<ClientKVReq> r = new Request<>();
                
                Response<String> response;

                //LOGGER.info("request content : {}, url : {}, put response : {}", obj.key + "=" + obj.getValue(), r.getUrl(), response.getResult());

                SleepHelper.sleep(1000);
                while(true)
                {
                	ClientKVReq obj = ClientKVReq.newBuilder().key(""+ii).type(ClientKVReq.GET).build();

                    addr = list.get(index);
                    r.setUrl(addr);
                    r.setObj(obj);
                    r.setCmd(Request.CLIENT_REQ);
                    Response<LogEntry> response2;
                    try {
                        response2 = client.send(r);
                    } catch (Exception e) {
                        r.setUrl(list.get((int) ((count.incrementAndGet()) % list.size())));
                        response2 = client.send(r);
                    }
                    if(response2.toString().equals("Response{result=ClientKVAck(result=null)}")) {
                    	break;
                    }
                    if(response2.getResult()==null)
                    	System.out.println("asd");

                    LOGGER.info("request content : {}, url : {}, get response : {}", obj.key + "=" + obj.getValue(), r.getUrl(), response2.getResult());
                    String modified_command=transRes(response2.toString());
                    op.fileOperation(modified_command);
                    ii++;
                }
                logIndex = ii;
                return ii;
            } catch (Exception e) {
                e.printStackTrace();
                i = i - 1;
            }

        }
        
	}
    
    private static String transRes(String string) {
    	String s = string.split(", ")[1].split("=")[1];
    	return s.substring(0,s.length() - 3);
	}

	public static void reset_request() throws RemotingException, InterruptedException {
		AtomicLong count = new AtomicLong(5);
		for (int i = 3; ; i++) {
            try {
            	int index = (int) (count.incrementAndGet() % list.size());
                addr = list.get(index);

                ClientKVReq obj = ClientKVReq.newBuilder().key(""+logIndex).value("reset").type(ClientKVReq.PUT).build();

                Request<ClientKVReq> r = new Request<>();
                r.setObj(obj);
                r.setUrl(addr);
                r.setCmd(Request.CLIENT_REQ);
                Response<String> response;
                try {
                    response = client.send(r);
                } catch (Exception e) {
                    r.setUrl(list.get((int) ((count.incrementAndGet()) % list.size())));
                    response = client.send(r);
                }

                LOGGER.info("request content : {}, url : {}, put response : {}", obj.key + "=" + obj.getValue(), r.getUrl(), response.getResult());
                if(response.toString().equals("Response{result=ClientKVAck(result=ok)}")) {
                	logIndex++;
                	break;
                }
                if(response.getResult()==null)
                	System.out.println("asd");

                LOGGER.info("request content : {}, url : {}, get response : {}", obj.key + "=" + obj.getValue(), r.getUrl(), response.getResult());
            } catch (Exception e) {
                e.printStackTrace();
                i = i - 1;
            }
        }
	}

}
