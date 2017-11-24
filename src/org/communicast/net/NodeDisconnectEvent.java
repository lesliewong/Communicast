package org.communicast.net;

import java.net.InetAddress;

/**
 * 节点断开事件
 * @author huangkaixuan
 *
 */
public interface NodeDisconnectEvent {
	public void trigger(InetAddress remote, int port, Node self);
}
