package org.communicast.msg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.communicast.net.Responser;

/**
 * ������Ϣ
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
		//�յ���������������һ������Ӧ��
//		if (!respon.isClient()) {
//			respon.reply(this);
//		}
	}

}
