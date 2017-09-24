package test.hashgold;

import java.io.IOException;

import exception.hashgold.ConnectionFull;
import net.hashgold.Node;

public class Test {

	public static void main(String[] args) throws IOException, ConnectionFull  {
		Node server = new Node();
		server.max_connections = 1;
		//server.debug = true;
		server.listen();
		
		Node client1 = new Node();
		Node client2 = new Node();
		client2.debug = true;
		client1.connect(server.getServerAddress(), server.getServerPort());
		try {
			Thread.currentThread().sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		client2.connect(server.getServerAddress(), server.getServerPort());
		server.waitForServer();
	}

}
