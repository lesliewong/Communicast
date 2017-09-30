package test.hashgold;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import collection.hashgold.LimitedRandomSet;
import exception.hashgold.ConnectionFull;
import net.hashgold.Node;

public class Test {

	public static void main(String[] args) throws IOException, ConnectionFull  {
//		Node server = new Node();
//		server.max_connections = 1;
//		//server.debug = true;
//		Set<InetSocketAddress> initSet = new HashSet<InetSocketAddress>();
//		server.listen();
//		initSet.add(new InetSocketAddress("202.12.31.77", 443));
//		initSet.add(new InetSocketAddress("2404:6800:8005::68", 80));
//		server.addPublicNodes(initSet);
//		
//		
//		
//		Node client1 = new Node();
//		client1.debug = true;
//		client1.connect(server.getServerAddress(), server.getServerPort());
//		server.waitForServer();
		try {
			LimitedRandomSet<Integer> set= new LimitedRandomSet<Integer>(10);
			Random rnd = new Random();
			System.out.println("添加元素");
			for(int i = 0; i< 8; i++) {
				int n = rnd.nextInt(100);
				set.add(n);
				System.out.print(n+",");
			}
			ArrayList<Integer> addList = new ArrayList<Integer> ();
			Collections.addAll(addList, new Integer[]{1000,2000,3000,1111});
			set.addAll(addList);
			System.out.println();
			System.out.println("留存元素");
			Iterator<Integer> it = set.iterator();
			while(it.hasNext()) {
				System.out.print(it.next()+",");
			}
			System.out.println();
			System.out.println("随机选择3元素");
			it = set.pick(3).iterator();
			while(it.hasNext()) {
				System.out.print(it.next()+",");
			}
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
