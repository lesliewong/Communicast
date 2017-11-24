package org.communicast.exception;

@SuppressWarnings("serial")
public class DuplicateMessageNumber extends Exception{
	public DuplicateMessageNumber (int number) {
		super("Message number " + number + " alreay in use");
	}
}
