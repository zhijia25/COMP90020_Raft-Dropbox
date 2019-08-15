package raft.client;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ListenerThread {
	private static ExecutorService fixedThreadPool = Executors.newCachedThreadPool();
	private WatchService ws;
	private String listenerPath;
	private File root;

	ListenerThread(String path) {
		try {
			this.ws = FileSystems.getDefault().newWatchService();
			this.listenerPath = path;
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void start() {
		fixedThreadPool.execute(new Listner(ws, this.listenerPath));
	}

	public void addListener(String path) throws IOException {
		Path p = Paths.get(path);
		p.register(this.ws, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE,
				StandardWatchEventKinds.ENTRY_CREATE);
		start();
	}
	
}