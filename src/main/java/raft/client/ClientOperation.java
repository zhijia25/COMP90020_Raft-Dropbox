package raft.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class ClientOperation {
	private File root;

	public ClientOperation() {
		try {
		this.root = new File("D:/test/");
		if (!this.root.exists()) {
			this.root.mkdirs();
		}}
		catch(Exception e) {
			System.out.println("error on initiating");
		}
	}

	public void fileOperation(String operation) throws ParseException, IOException {
//		JSONParser parse = new JSONParser();
//		JSONObject job = (JSONObject) parse.parse(operation);
		String value = operation;
		String[] details = value.split("- -- -- -");
		String action = details[0];
		String path = "D:/test/";
		if (action.equals("create")) {
			if (details.length > 2) {
				try {
					create(path + details[1]);
					modify(path + details[1], details[2]);
				} catch (Exception e) {
					System.out.println("create error");
				}
			} else {
				try {
					create(path + details[1]);
				} catch (Exception e) {
					System.out.print("some error in creating");
				}
			}
		}
		if (action.equals("delete")) {
			try {
				delete(path + details[1]);
			} catch (Exception e) {
				System.out.print("some error in deleting");
			}
		}
		if (action.equals("modify")) {
			if (details.length > 2) {
				modify(path + details[1], details[2]);
			} else {
				try {
					delete(path + details[1]);
					create(path + details[1]);
				} catch (Exception e) {
					System.out.println("some error on modyfying");
				}
			}
		}

	}

	private void create(String fileName) throws IOException {
		File file = new File(fileName);
		if (!file.exists())
			file.createNewFile();
	}

	private void delete(String fileName) {
		File file = new File(fileName);
		if (file.exists() && file.isFile())
			file.delete();
	}

	private void modify(String fileName, String content) {

		try {
			try {
				delete(fileName);
				create(fileName);
			} catch (Exception e) {
				System.out.println("I/O prob");
			}
			System.out.println(fileName);
			File file = new File(fileName);
			Writer out = new FileWriter(file);
			out.write(content);
			out.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void reset() {
		try {
			File[] fileList = this.root.listFiles();
			if (fileList.length > 0) {
				for (int i = 0; i < fileList.length; i++) {
					File f = new File(fileList[i].getPath().toString());
					if (f.exists()) {
						f.delete();
					}
				}
			} else {
				root.delete();
			}
			this.root.mkdirs();
			System.out.println("reset successfully");
		} catch (Exception e) {
			System.out.print("some error while reseting");
		}
	}
}
