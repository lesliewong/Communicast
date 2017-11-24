package org.communicast.msg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import org.communicast.collection.SocketAddressPacker;
import org.communicast.exception.HugeMessageException;
import org.communicast.net.Node;
import org.communicast.net.Responser;

/**
 * �����ڵ��б���
 * 
 * @author huangkaixuan
 *
 */
public class NodesExchange implements Message {
	private int listen_port; //���ؼ����˿�,0Ϊδ����

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
	public int getCode() {
		return 2;
	}

	@Override
	public void onReceive(Responser respon) {
		Node localNode = respon.getLocalNode();
			
		if (max_req > 0) {
			// ��������ڵ��б�
			try {
				NodesExchange reply = new NodesExchange(localNode, max_req);
				if (respon.isClient()) {
					reply.max_req = 0;
					//�ͻ��˸��������˿�
					reply.listen_port = localNode.getServerPort();
				}
				respon.reply(reply);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			if (!respon.isClient()) {
				//������ת���ͻ��˼�����ַ���ڽ�һ���ڵ�����ͨ��
				if (listen_port > 0 && Node.isInternetAddress(respon.getAddress())) {
					try {
						localNode.requestNeighbors(new ConnectivityDetectProxy(respon.getAddress(), listen_port),respon.getSock(), 1);
					} catch (HugeMessageException e) {
					}
				}
			}
		}
		
		boolean no_detect = false;
		if (respon.isClient()) {
			//�Է��Ƿ�������ӵ������ڵ��б���
			if (list == null) {
				list = new HashSet<InetSocketAddress>();
				no_detect = true;
			}
			list.add(new InetSocketAddress(respon.getAddress(), respon.getPort()));
		} 
		
		if (list != null) {
			// �ϲ��Է��Ĺ����ڵ��б�
			localNode.addAndSharePublicNodes(list, respon.getSock(), no_detect);
		}
		
		
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		// д��16λ�������������
		out.writeShort(max_req);
		
		//д��16λ�����˿�
		out.writeShort(listen_port);

		//д���ַ�б�
		SocketAddressPacker.flush(list, out);
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		//��ȡ��������
		max_req = in.readUnsignedShort();
		
		//��ȡ�����˿�
		listen_port = in.readUnsignedShort();
		
		//��ȡ��ַ�б�
		SocketAddressPacker.read(in, Node.public_nodes_list_size);
	}

}
