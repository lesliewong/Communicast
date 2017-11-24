package org.communicast.msg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;

import org.communicast.collection.SocketAddressPacker;
import org.communicast.exception.HugeMessageException;
import org.communicast.net.Node;
import org.communicast.net.Responser;

/**
 * ��ͨ�Դ���������
 * 
 * @author huangkaixuan
 *
 */
public class ConnectivityDetectProxy implements Message {
	private InetAddress addr;
	private int port;
	private RequestStatus status = RequestStatus.New;

	private enum RequestStatus {
		New, Fail, Success;
	}

	public ConnectivityDetectProxy() {
	}

	public ConnectivityDetectProxy(InetAddress addr, int port) {
		this.addr = addr;
		this.port = port;
	}

	@Override
	public int getCode() {
		return 5;
	}

	@Override
	public void onReceive(Responser respon) {
		if (!Node.isInternetAddress(addr) ) {
			return;
		}
		Node local = respon.getLocalNode();
		switch (status) {
		case New:
			//������
			//System.out.print("�յ��ڵ�������");
			this.status = local.detect(addr, port) ? RequestStatus.Success:RequestStatus.Fail;
			try {
				respon.reply(this);
			} catch (HugeMessageException e) {
			}
			break;
		case Fail:
			//System.out.print("�յ��ڵ���ʧ����Ӧ");
			//���ʧ��
			break;
		case Success:
			//System.out.print("�յ��ڵ���ɹ���Ӧ");
			//���ɹ�
			break;
		}
		//System.out.println(",ip:"+addr.getHostAddress() + " �˿�:" + port + ",���Զ˿�:"+respon.getPort());
		if (this.status == RequestStatus.Success) {
			Set<InetSocketAddress> singleSet = new HashSet<InetSocketAddress>();
			singleSet.add(new InetSocketAddress(addr, port));
			local.addAndSharePublicNodes(singleSet, respon.getSock(), true);
		}
	}

	@Override
	public void output(DataOutputStream out) throws IOException {
		out.write((byte)this.status.ordinal());
		out.write(addr.getAddress());
		out.writeShort(port);
	}

	@Override
	public void input(DataInputStream in, int len) throws IOException {
		int addrSize = len > SocketAddressPacker.IPv4Size + 3 ? SocketAddressPacker.IPv6Size:SocketAddressPacker.IPv4Size;
		status =RequestStatus.values()[in.read()];
		byte[] addrBuffer = new byte[addrSize];
		in.read(addrBuffer);
		addr = InetAddress.getByAddress(addrBuffer);
		port = in.readUnsignedShort();
	}

}
