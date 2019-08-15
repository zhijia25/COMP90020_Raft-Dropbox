package raft.client;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class Client {
    public static void main(String[] args) throws IOException, ParseException {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					String path = "C:/da_test";
			        ListenerThread fileListener = new ListenerThread(path);
			        ClientOperation operater = new ClientOperation();
			        fileListener.addListener(path);	   
					Scanner sc = new Scanner(System.in);					
					while (true) {
					System.out.println("please select your starting mode, 1 for synchronize, 2 for reset");
					String answer = sc.nextLine();
			        if (answer.equals("1")){
			        	operater.reset();
			        	System.out.println("you choose to synchronize your folder");
			        	break;
			        }
			        else if (answer.equals("2")) {
			        	operater.reset();
			        	System.out.println("you choose to reset your folder");
			        	break;
			        }
			        else {
			        	System.out.println("try again, please just enter 1 or 2");
			        }}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
    	
 
    }

}

