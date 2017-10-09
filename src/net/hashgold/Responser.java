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
	private final Message _msg;
	
	Responser(Node node, NodeSocket sock, Message sourceMessage) {
		_node = node;
		_sock = sock;
		_msg = sourceMessage;
	}
	
	/**
	 * 回复消息
	 * @param message
	 * @return
	 */
	public boolean reply(Message message) {
		return _node.sendTo(_sock, message);
	}
	
	/**
	 * 转发消息
	 * @param message
	 * @return
	 */
	public int forward() {
		return _node.forward(_msg, _sock);
	}
	
	
	/**
	 * 获取远程地址
	 * @return
	 */
	public InetAddress getAddress() {
		return _sock.getInetAddress();
	}
	
	
	/**
	 * 获取远程端口
	 * @return
	 */
	public int getPort() {
		return _sock.getPort();
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
	
	
	/**
	 * 关闭连接
	 */
	public void close() {
		_node.delConnected(_sock);
	}
}
