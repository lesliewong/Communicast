package net.hashgold;

import java.net.InetAddress;

import msg.hashgold.Message;

/**
 * ��Ϣ��Ӧ��
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
	
	/**
	 * �ظ���Ϣ
	 * @param message
	 * @return
	 */
	public boolean reply(Message message) {
		return _node.sendTo(_sock, message);
	}
	
	/**
	 * ת����Ϣ
	 * @param message
	 * @return
	 */
	public int forward(Message message) {
		return _node.forward(message, _sock);
	}
	
	
	/**
	 * ��ȡԶ�̵�ַ
	 * @return
	 */
	public InetAddress getAddress() {
		return _sock.getInetAddress();
	}
	
	
	/**
	 * ��ȡԶ�̶˿�
	 * @return
	 */
	public int getPort() {
		return _sock.getPort();
	}
	
	
	/**
	 * ��ȡ���ؽڵ�
	 * @return
	 */
	public Node getLocalNode() {
		return _node;
	}
	
	/**
	 * �ж������Ƿ�ͻ���
	 * @return
	 */
	public boolean isClient() {
		return _sock.isClient;
	}
	
	
	/**
	 * �ر�����
	 */
	public void close() {
		_node.delConnected(_sock);
	}
}
