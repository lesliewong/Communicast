package net.hashgold;

import java.net.InetAddress;

/**
 * �ڵ�Ͽ��¼�
 * @author huangkaixuan
 *
 */
public interface NodeDisconnectEvent {
	public void trigger(InetAddress remote, int port, Node self);
}
