package raft.client;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import com.alipay.remoting.exception.RemotingException;

class Listner implements Runnable {
	private WatchService service;
	private String rootPath;
	private int id ;

	public Listner(WatchService service, String rootPath) {
		this.service = service;
		this.rootPath = rootPath;
		this.id = 0;

	}

	public void run() {
		try {
			while (true) {
				ArrayList<JSONObject> command = new ArrayList();
				WatchKey watchKey = service.take();
				List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
				for (WatchEvent<?> event : watchEvents) {
					System.out.println(event.kind());
					id ++;
					if (event.kind().toString().equals("ENTRY_CREATE")) {
						JSONObject create = new JSONObject();
						command.add(create);
						create.put("key", id);
						String path = event.context().toString();
						String content ="create- -- -- -"+ event.context().toString();
						try {
							
							BufferedReader in = new BufferedReader(new FileReader(rootPath+"/"+event.context().toString()));
							String line = in.readLine();
							if (line != null) {
								content = content + "- -- -- -";
								while (line != null) {
									content = content + line+"\n";
									line = in.readLine();
								}
								create.put("value", content);
							}
							in.close();
						} catch (Exception e) {
							System.out.println("immediate change name when crteate");
						}
						create.put("value", content);
					}
					else if (event.kind().toString().equals("ENTRY_DELETE")) {
						JSONObject delete = new JSONObject();
						command.add(delete);
						delete.put("key", id);
						delete.put("value", "delete- -- -- -"+event.context().toString());
					}
					else if (event.kind().toString().equals("ENTRY_MODIFY")) {
						JSONObject modify = new JSONObject();
						command.add(modify);
						modify.put("key", id);
						String content ="modify- -- -- -"+ event.context().toString();
						try {
							BufferedReader in = new BufferedReader(new FileReader(rootPath+"/"+event.context().toString()));
							String line = in.readLine();
							if(line!=null) {
								content = content + "- -- -- -";
							}
							while (line != null) {								
								content = content + line+"\n";
								line = in.readLine();
							}						
							modify.put("value", content);
							in.close();
						
						} catch (Exception e) {
							System.out.println("no file");
						}
					}
				}
				for (int i = 0; i < command.size(); i++) {
					System.out.println(command.get(i).toJSONString());
					RaftClient.request(command.get(i));
				}
				watchKey.reset();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (RemotingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			System.out.println("fdsfsdf");
			try {
				service.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
}