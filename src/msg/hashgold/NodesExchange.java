package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import collection.hashgold.SocketAddressPacker;
import net.hashgold.Node;
import net.hashgold.Responser;

/**
 * 公共节点列表交换
 * 
 * @author huangkaixuan
 *
 */
public class NodesExchange implements Message {
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
		
		boolean no_detect = false;
		if (respon.isClient()) {
			//对方是服务器添加到公共节点列表检测
			if (list == null) {
				list = new HashSet<InetSocketAddress>();
				no_detect = true;
			}
			list.add(new InetSocketAddress(respon.getAddress(), respon.getPort()));
		} 
		
		if (list != null) {
			// 合并对方的公共节点列表
			localNode.addAndSharePublicNodes(list, respon.getSock(), no_detect);
		}
		
		
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		// 写入16位的最大请求数量
		out.writeShort(max_req);
		
		//写入16位监听端口
		out.writeShort(listen_port);

		//写入地址列表
		SocketAddressPacker.flush(list, out);
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		//读取请求数量
		max_req = in.readUnsignedShort();
		
		//读取监听端口
		listen_port = in.readUnsignedShort();
		
		//读取地址列表
		SocketAddressPacker.read(in, Node.public_nodes_list_size);
	}

}
