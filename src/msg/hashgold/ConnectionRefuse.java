package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.hashgold.Responser;

/**
 * 拒绝连接
 * @author leslie
 *
 */
public class ConnectionRefuse implements Message {
	private String prompt;
	private NodesExchange nodes_return;
	
	public ConnectionRefuse(){
	}
	
	public ConnectionRefuse(String message, NodesExchange nodesAvailable) {
		prompt = message;
		nodes_return = nodesAvailable;
	}
	
	@Override
	public int getCode() {
		return 3;
	}

	@Override
	public void onReceive(Responser respon) {
		nodes_return.onReceive(respon);
		System.err.println(respon.getAddress().getHostAddress() + " connection rejected:"+ this);
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		int start = out.size();
		out.writeUTF(prompt);//写入可读的消息
		out.writeShort(out.size() - start);//消息长度
		nodes_return.output(out);//可用节点列表
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		prompt = in.readUTF();
		nodes_return = new NodesExchange();
		nodes_return.input(in, len - in.readUnsignedShort() - 2);
	}
	
	public String toString() {
		return prompt;
	}

}
