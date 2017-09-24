package msg.hashgold;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.hashgold.Node;
import net.hashgold.NodeSocket;
import net.hashgold.Responser;

/**
 * 公共节点列表交换
 * @author huangkaixuan
 *
 */
public class NodesExchange implements Message {
	private  Set<InetSocketAddress> list;
	private  int max_req;
	
	public NodesExchange() {}
	
	public NodesExchange(int max_request,  Node node) throws Exception {
		if (max_request > 65535 || max_request <= 0) {
			throw new Exception("Invalid public nodes list length");
		}
		max_req = max_request;
		list = node.getPublicNodes();
	}
	
	@Override
	public int getType() {
		return 2;
	}

	@Override
	public void onReceive(Responser respon) {
		// TODO Auto-generated method stub

	}

	@Override
	public void output(OutputStream out) throws IOException {
		//写入16位发送数量
		int sizeToSend = list.size();
		out.write(sizeToSend >> 8);
		out.write(sizeToSend);
		
		//写入16位的最大响应数量
		out.write(max_req >> 8);
		out.write(max_req);
		
		//写入若干比特IP版本标识,0 v4 1 v6
		ArrayList<InetSocketAddress> sorted = new ArrayList<InetSocketAddress>(list);
		byte flag = 0;
		int bitIndex = 0;
		InetAddress addr;
		for(int i = 0; i < sizeToSend; i++) {
			addr = sorted.get(i).getAddress();
			if (addr instanceof Inet6Address) {
				flag |= 0x80 >>> bitIndex;
			}
			
			bitIndex++;
			bitIndex %= 8;
			if (bitIndex == 0) {
				out.write(flag);
				flag = 0;
			}
		}
		if (sizeToSend % 8 != 0) {
			out.write(flag);
		}
		
		//写入socket地址列表
		InetSocketAddress socket_addr;
		for(int i = 0; i < sizeToSend; i++) {
			socket_addr = sorted.get(i);
			out.write(socket_addr.getAddress().getAddress());
			out.write(socket_addr.getPort() >> 8);
			out.write(socket_addr.getPort());
		}
		

	}

	@Override
	public void input(InputStream in, int len) throws IOException {
		// TODO Auto-generated method stub

	}

}
