package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import exception.hashgold.HugeMessageException;
import net.hashgold.Responser;

/**
 * �ڵ�̽����Ϣ
 * @author huangkaixuan
 *
 */

public class NodeDetection implements Message {

	@Override
	public int getCode() {
		return 1;
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {}

	@Override
	public void onReceive(Responser respon) {
		if (! respon.isClient()) {//����˷���̽����Ӧ
			try {
				respon.reply(this);
			} catch (HugeMessageException e) {
			}
		}	
	}

}
