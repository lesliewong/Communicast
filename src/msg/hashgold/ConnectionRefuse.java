package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.hashgold.Responser;

/**
 * ¾Ü¾øÁ¬½Ó
 * @author leslie
 *
 */
public class ConnectionRefuse implements Message {
	private byte[] msgBytes;
	
	public ConnectionRefuse(){
		msgBytes = new byte[0];
	}
	
	public ConnectionRefuse(String message) {
		msgBytes = message.getBytes();
	}
	
	@Override
	public int getType() {
		return 3;
	}

	@Override
	public void onReceive(Responser respon) {
		System.out.println(respon.getAddress().getHostAddress() + " refused your connection:"+ this);
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		out.write(msgBytes);
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		msgBytes = new byte[len];
		in.read(msgBytes);
	}
	
	public String toString() {
		return new String(msgBytes);
	}

}
