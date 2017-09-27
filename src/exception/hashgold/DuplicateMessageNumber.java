package exception.hashgold;

@SuppressWarnings("serial")
public class DuplicateMessageNumber extends Exception{
	public DuplicateMessageNumber (int number) {
		super("Message number " + number + " alreay in use");
	}
}
