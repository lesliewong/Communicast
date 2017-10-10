package test.hashgold;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import collection.hashgold.BloomFilter;
import exception.hashgold.AlreadyConnected;
import exception.hashgold.ConnectionFull;
import msg.hashgold.HelloWorld;
import net.hashgold.Node;

public class Test {
	
	private static class ListenThread extends Thread {
		private static final ThreadGroup DemonThreads;
		private final Node node;
		private final int port;
		private final InetAddress addr;
		static {
			DemonThreads = new ThreadGroup("Demon");
		}
		
		public static void interuptAll() {
			DemonThreads.interrupt();
		}
		
		ListenThread(int port, String addr, boolean debug) throws UnknownHostException {
			super(DemonThreads, "thread" + new Random().nextInt());
			node = new Node();
			node.debug = debug;
			this.port = port;
			this.addr = InetAddress.getByName(addr);
			this.setDaemon(true);
			this.start();
		}
		
		ListenThread(boolean debug) {
			super(DemonThreads, "thread" + new Random().nextInt());
			node = new Node();
			node.debug = debug;
			port = 0;
			addr = null;
			this.setDaemon(true);
			this.start();
		}
		
		ListenThread(int port,boolean debug) {
			super(DemonThreads, "thread" + new Random().nextInt());
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
		//运行6个节点
		ListenThread th_node1 = new ListenThread(9000, false);
		ListenThread th_node2 = new ListenThread(9001, false);
		ListenThread th_node3 = new ListenThread(9002, false);
		ListenThread th_node4 = new ListenThread(9003, false);
		ListenThread th_node5 = new ListenThread(9004, false);
		ListenThread th_node6 = new ListenThread(9005, false);
		
		Node node1 = th_node1.getNode();
		Node node2 = th_node2.getNode();
		Node node3 = th_node3.getNode();
		Node node4 = th_node4.getNode();
		Node node5 = th_node5.getNode();
		Node node6 = th_node6.getNode();
		
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
		
		//6-->5
		node6.connect(node5.getServerAddress(), node5.getServerPort());
		
		//从节点2广播一条消息,消息可被3,4在内的所有节点收到
		node6.broadcast(new HelloWorld("Dear,this is node 6 speaking"));

		Thread.sleep(5000);
		node1.shutdown();
		node2.shutdown();
		node3.shutdown();
		node4.shutdown();
		node5.shutdown();
		node6.shutdown();
		
		
//		try {
//			BloomFilter filter = new BloomFilter(512, 0.5);
//			int val;
//			Random rnd = new Random();
//			Set<Integer> the_set = new HashSet<Integer>();
//			int rightCount = 0;
//			int testNum = 500000;
//			for(int i = 1; i <= testNum; i++) {
//				val = rnd.nextInt();
//				if (filter.add((""+val).getBytes())) {
//					
//					//System.out.println(i + ". " +val + " 不存在,已添加");
//					if (the_set.contains(val)) {
//						//System.out.println("判断错误!");
//					} else {
//						//System.out.println("判断正确!");
//						rightCount++;
//					}
//					the_set.add(val);
//				} else {
//					//System.out.println(i + ". " +val + " 添加过!");
//					if (the_set.contains(val)) {
//						//System.out.println("判断正确!");
//						rightCount++;
//					} else {
//						//System.out.println("判断错误!");
//					}
//				}
//			}
//			
//			System.out.println("正确率:"+ String.format("%.2f", (double)rightCount/testNum * 100) + "%");
//			
//			
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
	}

}
