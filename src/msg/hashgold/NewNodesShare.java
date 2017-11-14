package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;

import collection.hashgold.SocketAddressPacker;
import net.hashgold.Node;
import net.hashgold.Responser;

/**
 * 共享新发现的公共节点
 * @author huangkaixuan
 *
 */
public class NewNodesShare implements Message {
	private Set<InetSocketAddress> _list;
	public NewNodesShare() {}
	
	public NewNodesShare(Set<InetSocketAddress> addresses) {
		_list = addresses;
	}
	
	@Override
	public int getType() {
		return 6;
	}

	@Override
	public void onReceive(Responser respon) {
		if (_list != null && _list.size() > 0) {
			//向除来源外的邻居共享公共节点
			respon.getLocalNode().addAndSharePublicNodes(_list, respon.getSock(), false);	
		}
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		SocketAddressPacker.flush(_list, out);
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		_list = SocketAddressPacker.read(in, Node.public_nodes_list_size);
	}

}
