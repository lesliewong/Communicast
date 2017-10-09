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
	private final Message _msg;
	
	Responser(Node node, NodeSocket sock, Message sourceMessage) {
		_node = node;
		_sock = sock;
		_msg = sourceMessage;
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
		return _node.forward(_msg, _sock);
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
