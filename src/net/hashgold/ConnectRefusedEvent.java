package net.hashgold;

import java.net.InetAddress;

import msg.hashgold.Message;

public interface ConnectRefusedEvent {
	public void trigger(InetAddress remote, int port, Node self);
}
