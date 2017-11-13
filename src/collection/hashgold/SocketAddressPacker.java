/**
 * 
 */
package collection.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * 网络地址列表
 * 节点交换,新节点共享,节点代理检测调用
 * @author huangkaixuan
 *
 */
public class SocketAddressPacker {
	public static final int IPv4Size = 4;
	public static final int IPv6Size = 16;
	
	/**
	 * 输出地址列表到输出流
	 * @param list
	 * @param out
	 * @throws IOException
	 */
	public static void flush(Set<InetSocketAddress> list, DataOutputStream out) throws IOException {
				// 写入16位发送地址数量
				int sizeToSend = list == null ? 0 : list.size();
				out.writeShort(sizeToSend);
				
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
	
	/**
	 * 从流读取地址列表
	 * @param in
	 * @param limit 读入地址最大数量
	 * @return
	 * @throws IOException
	 */
	public static Set<InetSocketAddress> read(DataInputStream in, int limit) throws IOException {
		Set<InetSocketAddress> list;
		int sizeReceived = in.readUnsignedShort();
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
				if (limit > 0 && i >= limit) {
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
		
		return list;
	}
}
