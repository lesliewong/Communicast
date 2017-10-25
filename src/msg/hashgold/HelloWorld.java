package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.hashgold.Responser;

/**
 * 保留的测试消息
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
	public int getType() {
		return 4;
	}

	@Override
	public void onReceive(Responser respon) {
		respon.close();
//		System.out.println("收到世界问候:" + message + " 时间:" +new Date(send_time*1000) + "本机端口:" + respon.getLocalNode().getServerPort() );
//		//转发
//		respon.forward();
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
