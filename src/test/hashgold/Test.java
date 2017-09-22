package test.hashgold;

import java.io.IOException;

import exception.hashgold.ConnectionFull;
import net.hashgold.Node;

public class Test {

	public static void main(String[] args) throws IOException, ConnectionFull  {
		Node server = new Node();
		server.listen();
		server.debug = true;
		Node client = new Node();
		System.out.println(client.detect(server.getServerAddress(), server.getServerPort()) ? "服务器存活":"服务器无响应");
		
		server.waitForServer();
	}

}
