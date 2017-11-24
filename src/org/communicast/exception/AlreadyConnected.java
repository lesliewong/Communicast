package org.communicast.exception;

@SuppressWarnings("serial")
public class AlreadyConnected extends Exception {
	public AlreadyConnected(String remote) {
		super(remote);
	}
}
