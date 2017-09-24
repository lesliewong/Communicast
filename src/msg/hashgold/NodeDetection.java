package msg.hashgold;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.hashgold.Responser;

/**
 * �ڵ�̽����Ϣ
 * @author huangkaixuan
 *
 */

public class NodeDetection implements Message {

	@Override
	public int getType() {
		return 1;
	}

	@Override
	public void output(OutputStream out) throws IOException {
		
	}

	@Override
	public void input(InputStream in, int len) throws IOException {}

	@Override
	public void onReceive(Responser respon) {
		if (! respon.isClient()) {//����˷���̽����Ӧ
			respon.reply(this);
		}	
	}

}
