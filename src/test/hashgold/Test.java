package test.hashgold;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicLong;

import exception.hashgold.AlreadyConnected;
import exception.hashgold.ConnectionFull;
import exception.hashgold.DuplicateMessageNumber;
import msg.hashgold.HelloWorld;
import msg.hashgold.Registry;
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
			node = new Node("demo net");
			node.debug = debug;
			this.port = port;
			this.addr = InetAddress.getByName(addr);
			this.setDaemon(true);
			this.start();
		}
		
		ListenThread(boolean debug) {
			super(DemonThreads, "thread" + new Random().nextInt());
			node = new Node("demo net");
			node.debug = debug;
			port = 0;
			addr = null;
			this.setDaemon(true);
			this.start();
		}
		
		ListenThread(int port,boolean debug) {
			super(DemonThreads, "thread" + new Random().nextInt());
			node = new Node("demo net");
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
					node.listen(port, null);
				} else {
					node.listen(port, 50, addr, null);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(2);
			}
		}
	}

	public static void main(String[] args) throws IOException, ConnectionFull, InterruptedException, AlreadyConnected  {
		AtomicLong counter = new AtomicLong();
		ThreadGroup hashGroup = new ThreadGroup("hashes");
		String hashName = "SHA-512";
		int testTime = 10;
		int threadNum = 8;
		byte[] toTest = "Hello World".getBytes();
		System.out.println("����" +threadNum+"���̼߳����ϣ..");
		for(int i = 0 ; i < threadNum; i++) {
			new Thread(hashGroup, new Runnable() {
				@Override
				public void run() {
					// TODO ����hash
					byte[] hash = new byte[512];
					System.arraycopy(toTest, 0, hash, 0, toTest.length);
					try {
						MessageDigest digest  = MessageDigest.getInstance(hashName);
						do {
						hash = digest.digest(hash);
						counter.incrementAndGet();
						if (Thread.interrupted()) {
							break;
						}
						} while(true);
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
				}
				
			}).start();
		}
		System.out.println("�ȴ�" + testTime+ "��������..");
		Thread.sleep(testTime*1000);
		hashGroup.interrupt();
		System.out.println(hashName + "��ϣ��:"+counter.longValue() /testTime + " hash/s");
		
//		Scanner in = new Scanner(System.in);
//		in.nextLine();
//		try {
//			Registry.registerMessage(new HelloWorld());
//		} catch (DuplicateMessageNumber e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		//����6���ڵ�
//		ListenThread th_node1 = new ListenThread(9000, false);
//		ListenThread th_node2 = new ListenThread(9001, true);
//		ListenThread th_node3 = new ListenThread(9002, false);
//		ListenThread th_node4 = new ListenThread(9003, false);
//		ListenThread th_node5 = new ListenThread(9004, false);
//		ListenThread th_node6 = new ListenThread(9005, false);
//		
//		Node node1 = th_node1.getNode();
//		Node node2 = th_node2.getNode();
//		Node node3 = th_node3.getNode();
//		Node node4 = th_node4.getNode();
//		Node node5 = th_node5.getNode();
//		Node node6 = th_node6.getNode();
//		Node node7 = new Node(null);
//		Node node8 = new Node("network 2");
//		Node node9 = new Node("network 2");
//		
////		//���绥��
////		//2 -->1,2 --> 5
//		node2.connect(node1.getServerAddress(), node1.getServerPort());
//		node2.connect(node5.getServerAddress(), node5.getServerPort());
////		
////		//3 -->1,3 --> 2
//		Thread.sleep(1000);
//		node3.connect(node1.getServerAddress(), node1.getServerPort());
//		node3.connect(node2.getServerAddress(), node2.getServerPort());
//		
//		//4 -->1,4 --> 3
//		node4.connect(node1.getServerAddress(), node1.getServerPort());
//		node4.connect(node3.getServerAddress(), node3.getServerPort());
//		
//		//5 -->1,5 --> 4
//		node5.connect(node1.getServerAddress(), node1.getServerPort());
//		node5.connect(node4.getServerAddress(), node4.getServerPort());
//		
//		//6-->5
//		node6.connect(node5.getServerAddress(), node5.getServerPort());
//		
//		//7-->1
//		node7.connect(node1.getServerAddress(), node1.getServerPort());
//		
//		//8-->2
//		node8.connect(node2.getServerAddress(), node2.getServerPort());
//		
//		//9-->4
//		node9.connect(node4.getServerAddress(), node4.getServerPort());
//		
//		Thread.sleep(2000);
//		//�ӽڵ�6�㲥һ����Ϣ,��Ϣ�ɱ�3,4���ڵ����нڵ��յ�
//		node6.broadcast(new HelloWorld("Dear,this is node 6 speaking"));
//		
//		//�ӽڵ�9�㲥һ����Ϣ,��Ϣ�ɱ�8�յ�
//		//node9.broadcast(new HelloWorld("Dear,this is node 9 speaking"));
//		
//		Thread.sleep(60000);
//		node1.shutdown();
//		node2.shutdown();
//		node3.shutdown();
//		node4.shutdown();
//		node5.shutdown();
//		node6.shutdown();
//		node7.shutdown();
//		node8.shutdown();
//		node9.shutdown();
	}

}
