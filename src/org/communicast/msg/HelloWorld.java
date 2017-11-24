package org.communicast.msg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

import org.communicast.net.Responser;

/**
 * �����Ĳ�����Ϣ
 * @author huangkaixuan
 *
 */
public class HelloWorld implements Message {
	private int send_time;
	private String message;
	public HelloWorld() {}
	
	public HelloWorld(String message) {
		this.message = message;
		send_time = (int) (System.currentTimeMillis() / 1000);
	}
	
	@Override
	public int getCode() {
		return 4;
	}

	@Override
	public void onReceive(Responser respon) {
		//respon.close();
		System.out.println("�յ������ʺ�:" + message + " ʱ��:" +new Date(send_time*1000) + "Զ�̶˿�:" + respon.getPort());
		//ת��
		respon.forward();
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		out.writeUTF(message);
		out.writeInt(send_time);
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		message = in.readUTF();
		send_time = in.readInt();

	}

}
