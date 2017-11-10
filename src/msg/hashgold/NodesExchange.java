package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.hashgold.Node;
import net.hashgold.Responser;

/**
 * 公共节点列表交换
 * 
 * @author huangkaixuan
 *
 */
public class NodesExchange implements Message {
	static final int IPv4Size = 4;
	static final int IPv6Size = 16;
	private int listen_port; //本地监听端口,0为未监听

	private Set<InetSocketAddress> list;
	public int max_req;

	public NodesExchange() {
	}

	public NodesExchange(Node localNode, int num_to_send) throws Exception {
		if (num_to_send > 65535 || num_to_send < 0) {
			throw new Exception("Invalid public nodes list length to send");
		}

		max_req = Math.max(localNode.getPublicNodesList().size(),
				Node.public_nodes_list_size - localNode.getPublicNodesList().size());
		list = localNode.getPublicNodes(num_to_send);
	}

	@Override
	public int getType() {
		return 2;
	}

	@Override
	public void onReceive(Responser respon) {
		Node localNode = respon.getLocalNode();
			
		if (max_req > 0) {
			// 分享自身节点列表
			try {
				NodesExchange reply = new NodesExchange(localNode, max_req);
				if (respon.isClient()) {
					reply.max_req = 0;
					//客户端附带监听端口
					reply.listen_port = localNode.getServerPort();
				}
				respon.reply(reply);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			if (!respon.isClient()) {
				//服务器转发客户端监听地址给邻近一个节点检测连通性
				if (listen_port > 0 && Node.isInternetAddress(respon.getAddress())) {
					localNode.requestNeighbors(new ConnectivityDetectProxy(respon.getAddress(), listen_port),respon.getSock(), 1);
				}
			}
		}
		
		if (list != null) {
			// 合并对方的公共节点列表
			if (respon.isClient()) {
				//对方是服务器添加到公共节点列表检测
				list.add(new InetSocketAddress(respon.getAddress(), respon.getPort()));
			} 
			localNode.addPublicNodes(list);
		}
		
		
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		// 写入16位发送数量
		int sizeToSend = list == null ? 0 : list.size();
		out.writeShort(sizeToSend);

		// 写入16位的最大响应数量
		out.writeShort(max_req);
		
		//写入16位监听端口
		out.writeShort(listen_port);

		if (sizeToSend > 0) {
			// 写入若干比特IP版本标识,0 v4 1 v6
			byte flag = 0;
			int bitIndex = 0;
			InetAddress addr;
			Iterator<InetSocketAddress> it = list.iterator();
			do {
				addr = it.next().getAddress();
				if (addr instanceof Inet6Address) {
					flag |= 0x80 >>> bitIndex;
				}

				bitIndex++;
				bitIndex %= 8;
				if (bitIndex == 0) {
					out.write(flag);
					flag = 0;
				}
			} while (it.hasNext());

			if (sizeToSend % 8 != 0) {
				out.write(flag);
			}

			// 写入socket地址列表
			InetSocketAddress socket_addr;
			it = list.iterator();
			do {
				socket_addr = it.next();
				out.write(socket_addr.getAddress().getAddress());
				out.writeShort(socket_addr.getPort());
			} while (it.hasNext());
		}

	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		// 列表长度,请求数量,监听端口
		int sizeReceived = in.readUnsignedShort();
		max_req = in.readUnsignedShort();
		listen_port = in.readUnsignedShort();

		if (sizeReceived > 0) {
			// 读取标识位
			byte[] flagBytes = new byte[(int) Math.ceil((float)sizeReceived / 8)];
			in.read(flagBytes);

			// 读取节点列表
			byte[] ipv4 = new byte[IPv4Size];
			byte[] ipv6 = new byte[IPv6Size];
			InetAddress addr;
			list = new HashSet<InetSocketAddress>();
			for (int i = 0; i < sizeReceived; i++) {
				if (i >= Node.public_nodes_list_size) {
					break;
				}

				if (((flagBytes[i / 8] << (i % 8)) & 0xff) == 0x80) {
					// IP v6
					in.read(ipv6);
					addr = InetAddress.getByAddress(ipv6);
				} else {
					// IP v4
					in.read(ipv4);
					addr = InetAddress.getByAddress(ipv4);
				}
				list.add(new InetSocketAddress(addr, in.readUnsignedShort()));
			}
		} else {
			list = null;
		}
	}

}
