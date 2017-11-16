package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.hashgold.Responser;

/**
 * 心跳消息
 * @author huangkaixuan
 *
 */

public class HeartBeat implements Message {

	@Override
	public int getCode() {
		return 0;
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
	}

	@Override
	public void input(DataInputStream in, int len) {
	}

	@Override
	public void onReceive(Responser respon) {
		//收到心跳服务器返回一个心跳应答
//		if (!respon.isClient()) {
//			respon.reply(this);
//		}
	}

}
