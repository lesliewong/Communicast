package net.hashgold;

import java.net.InetAddress;

import msg.hashgold.Message;

/**
 * ÏûÏ¢ÏìÓ¦Æ÷
 * @author huangkaixuan
 *
 */
public final class Responser {
	private final Node _node;
	private final NodeSocket _sock;
	
	Responser(Node node, NodeSocket sock) {
		_node = node;
		_sock = sock;
	}
	
	public boolean send(Message message) {
		return _node.sendTo(_sock, message);
	}
	
	public InetAddress getAddress() {
		return _sock.getInetAddress();
	}
	
	public int getPort() {
		return _sock.getPort();
	}
	
	public boolean isClient() {
		return _sock.isClient;
	}
	
	public void close() {
		_node.delConnected(_sock);
	}
}
