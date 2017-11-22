package exception.hashgold;

@SuppressWarnings("serial")
public class NodesNotEnough extends Exception {
	public NodesNotEnough(int require, int have) {
		super("Connected nodes not engouth," + require + " required,current connected:" + have);
	}
}
