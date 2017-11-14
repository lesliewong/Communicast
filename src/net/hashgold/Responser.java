package net.hashgold;

import java.net.InetAddress;

import msg.hashgold.Message;

/**
 * 消息响应器
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
	 * 回复消息
	 * @param message
	 * @return
	 */
	public boolean reply(Message message) {
		if (_sock == null) {
			return false;
		}
		return _node.sendTo(_sock, message);
	}
	
	/**
	 * 转发消息
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
	 * 获取远程地址
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
	 * 通过当前节点广播消息
	 * @param message
	 * @return
	 */
	public int broadcast(Message message) {
		return _node.broadcast(message);
	}
	
	
	/**
	 * 获取远程端口
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
	 * 获取本地节点
	 * @return
	 */
	public Node getLocalNode() {
		return _node;
	}
	
	/**
	 * 判断自身是否客户端
	 * @return
	 */
	public boolean isClient() {
		return _sock.isClient;
	}
	
	public NodeSocket getSock() {
		return _sock;
	}
	
	
	/**
	 * 关闭连接
	 */
	public void close() {
		_node.delConnected(_sock);
	}
}
