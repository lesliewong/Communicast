package msg.hashgold;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.hashgold.Responser;

/**
 * ������Ϣ
 * @author huangkaixuan
 *
 */

public class HeartBeat implements Message {
	
	@Override
	public int getType() {
		return 0;
	}

	@Override
	public void output(OutputStream out) throws IOException {
	}

	@Override
	public void input(InputStream in, int len) {
	}

	@Override
	public void onReceive(Responser respon) {
		//�յ���������������һ������Ӧ��
		if (!respon.isClient()) {
			respon.send(this);
		}
	}

}
