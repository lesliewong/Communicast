package org.communicast.net;

import java.net.InetAddress;

import org.communicast.exception.HugeMessageException;
import org.communicast.msg.Message;

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
	 * @throws HugeMessageException 
	 */
	public boolean reply(Message message) throws HugeMessageException {
		if (_sock == null) {
			return false;
		}
		return _node.sendTo(_sock, message);
	}
	
	/**
	 * ת����Ϣ
	 * @param message
	 * @return
	 */
	public int forward() {
		if (_sock !=null && _msg != null) {
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
		if (_sock == null) {
			return InetAddress.getLoopbackAddress();
		} else {
			return _sock.getInetAddress();
		}
	}
	
	/**
	 * ͨ����ǰ�ڵ�㲥��Ϣ
	 * @param message
	 * @return
	 * @throws Exception 
	 */
	public void broadcast(Message message, int min_nodes) throws Exception {
		 _node.broadcast(message, min_nodes);
	}
	
	
	/**
	 * ��ȡԶ�̶˿�
	 * @return
	 */
	public int getPort() {
		if (_sock == null) {
			return 0;
		} else {
			return _sock.getPort();
		}
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
