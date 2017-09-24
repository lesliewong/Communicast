package msg.hashgold;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.hashgold.Responser;

/**
 * 心跳消息
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
		//收到心跳服务器返回一个心跳应答
		if (!respon.isClient()) {
			respon.reply(this);
		}
	}

}
