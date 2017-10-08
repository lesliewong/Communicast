package test.hashgold;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import exception.hashgold.ConnectionFull;
import net.hashgold.Node;

public class Test {
	
	private static class ListenThread extends Thread {
		private final Node node;
		private final int port;
		private final InetAddress addr;
		
		ListenThread(int port, String addr, boolean debug) throws UnknownHostException {
			node = new Node();
			node.debug = debug;
			this.port = port;
			this.addr = InetAddress.getByName(addr);
			this.setDaemon(true);
			this.start();
		}
		
		ListenThread(boolean debug) {
			node = new Node();
			node.debug = debug;
			port = 0;
			addr = null;
			this.setDaemon(true);
			this.start();
		}
		
		ListenThread(int port,boolean debug) {
			node = new Node();
			node.debug = debug;
			this.port = port;
			addr = null;
			this.setDaemon(true);
			this.start();
		}
		
		public Node getNode() {
			return node;
		}
		
		public void run() {
			try {
				if (port == 0) {
					node.listen();
				} else if(addr == null) {
					node.listen(port);
				} else {
					node.listen(port, 50, addr);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(2);
			}
		}
	}

	public static void main(String[] args) throws IOException, ConnectionFull, InterruptedException  {
		Thread server = new ListenThread(9000, true);
		Node client = new Node();
		client.debug = true;
		client.connect(InetAddress.getLocalHost(), 9000);
		server.join();
		
	}

}
