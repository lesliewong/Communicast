package org.communicast.net;

import java.net.InetAddress;

public interface ConnectRefusedEvent {
	public void trigger(InetAddress remote, int port, Node self);
}
