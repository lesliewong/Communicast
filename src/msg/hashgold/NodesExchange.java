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
	private final int IPv4Size = 4;
	private final int IPv6Size = 16;

	private Set<InetSocketAddress> list;
	private int max_req;

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
		if (max_req > 0) {
			// 分享自身节点列表
			try {
				NodesExchange reply = new NodesExchange(respon.getLocalNode(), max_req);
				if (respon.isClient()) {
					reply.max_req = 0;
				}
				respon.reply(reply);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (list != null) {
			// 合并对方的公共节点列表
			if (respon.isClient()) {
				list.add(new InetSocketAddress(respon.getAddress(), respon.getPort()));
			}
			respon.getLocalNode().addPublicNodes(list);
		}
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		// 写入16位发送数量
		int sizeToSend = list == null ? 0 : list.size();
		out.writeShort(sizeToSend);

		// 写入16位的最大响应数量
		out.writeShort(max_req);

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
		// 列表长度,请求数量
		int sizeReceived = in.readUnsignedShort();
		max_req = in.readUnsignedShort();
		len -= 4;

		if (sizeReceived > 0) {
			// 读取标识位
			byte[] flagBytes = new byte[(int) Math.ceil((float)sizeReceived / 8)];
			in.read(flagBytes);
			len -= flagBytes.length;

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
					len -= IPv6Size;
				} else {
					// IP v4
					in.read(ipv4);
					addr = InetAddress.getByAddress(ipv4);
					len -= IPv4Size;
				}
				list.add(new InetSocketAddress(addr, in.readUnsignedShort()));
				len -= 2;
			}
		} else {
			list = null;
		}

		in.skip(len);
	}

}
