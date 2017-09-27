package test.hashgold;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import exception.hashgold.ConnectionFull;
import net.hashgold.Node;

public class Test {

	public static void main(String[] args) throws IOException, ConnectionFull  {
		Node server = new Node();
		server.max_connections = 1;
		//server.debug = true;
		Set<InetSocketAddress> initSet = new HashSet<InetSocketAddress>();
		server.listen();
		initSet.add(new InetSocketAddress("202.12.31.77", 443));
		initSet.add(new InetSocketAddress("2404:6800:8005::68", 80));
		server.addPublicNodes(initSet);
		
		
		
		Node client1 = new Node();
		client1.debug = true;
		client1.connect(server.getServerAddress(), server.getServerPort());
		server.waitForServer();
	}

}
