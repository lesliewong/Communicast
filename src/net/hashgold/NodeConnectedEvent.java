package net.hashgold;

import java.net.InetAddress;

/**
 * �ڵ������¼�
 * @author huangkaixuan
 *
 */
public interface NodeConnectedEvent {
	public void trigger(InetAddress remote, int port, Node node);
}
