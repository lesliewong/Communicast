package exception.hashgold;

@SuppressWarnings("serial")
public class HugeMessageException extends Exception {
	private final int max_length;
	public HugeMessageException( int max) {
		super("Message exceeded the maximum length:" + max);
		max_length = max;
	}
	
	public int getMaxLength() {
		return max_length;
	}
}
