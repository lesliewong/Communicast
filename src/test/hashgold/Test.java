package test.hashgold;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import exception.hashgold.AlreadyConnected;
import exception.hashgold.ConnectionFull;
import msg.hashgold.HelloWorld;
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

	public static void main(String[] args) throws IOException, ConnectionFull, InterruptedException, AlreadyConnected  {
		//运行5个节点
		ListenThread th_node1 = new ListenThread(9000, false);
		ListenThread th_node2 = new ListenThread(9001, false);
		ListenThread th_node3 = new ListenThread(9002, true);
		ListenThread th_node4 = new ListenThread(9003, true);
		ListenThread th_node5 = new ListenThread(9004, false);
		
		Node node1 = th_node1.getNode();
		Node node2 = th_node2.getNode();
		Node node3 = th_node3.getNode();
		Node node4 = th_node4.getNode();
		Node node5 = th_node5.getNode();
		
		//网络互联
		//2 -->1,2 --> 5
		node2.connect(node1.getServerAddress(), node1.getServerPort());
		node2.connect(node5.getServerAddress(), node5.getServerPort());
		
		//3 -->1,3 --> 2
		node3.connect(node1.getServerAddress(), node1.getServerPort());
		node3.connect(node2.getServerAddress(), node2.getServerPort());
		
		//4 -->1,4 --> 3
		node4.connect(node1.getServerAddress(), node1.getServerPort());
		node4.connect(node3.getServerAddress(), node3.getServerPort());
		
		//5 -->1,5 --> 4
		node5.connect(node1.getServerAddress(), node1.getServerPort());
		node5.connect(node4.getServerAddress(), node4.getServerPort());
		
		//从节点2广播一条消息,消息可被3,4在内的所有节点收到
		node2.broadcast(new HelloWorld("Dear,this is huangkaixuan speaking"));
		
		
		th_node5.join();
		
	}

}
