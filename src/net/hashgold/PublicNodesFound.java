package net.hashgold;

import java.net.InetSocketAddress;
import java.util.Set;

public interface PublicNodesFound {
	public void trigger(Set<InetSocketAddress> addresses);
}
