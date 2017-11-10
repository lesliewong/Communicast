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
	private final byte[] _msg;
	
	Responser(Node node, NodeSocket sock, byte[] rawMessage) {
		_node = node;
		_sock = sock;
		_msg = rawMessage;
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
	public int forward() {
		if (_msg != null) {
			return _node.flood(_msg, _sock);
		} else {
			return 0;
		}
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
	
	public NodeSocket getSock() {
		return _sock;
	}
	
	
	/**
	 * �ر�����
	 */
	public void close() {
		_node.delConnected(_sock);
	}
}
