package msg.hashgold;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import net.hashgold.Node;
import net.hashgold.Responser;

/**
 * 连通性代理检测请求
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
	public int getType() {
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
			//请求检测
			//System.out.print("收到节点检测请求");
			this.status = local.detect(addr, port) ? RequestStatus.Success:RequestStatus.Fail;
			respon.reply(this);
			break;
		case Fail:
			//System.out.print("收到节点检测失败响应");
			//监测失败
			break;
		case Success:
			//System.out.print("收到节点检测成功响应");
			//监测成功
			break;
		}
		//System.out.println(",ip:"+addr.getHostAddress() + " 端口:" + port + ",来自端口:"+respon.getPort());
		if (this.status == RequestStatus.Success) {
			local.getPublicNodesList().add(new InetSocketAddress(addr, port));
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
		int addrSize = len > NodesExchange.IPv4Size + 3 ? NodesExchange.IPv6Size:NodesExchange.IPv4Size;
		status =RequestStatus.values()[in.read()];
		byte[] addrBuffer = new byte[addrSize];
		in.read(addrBuffer);
		addr = InetAddress.getByAddress(addrBuffer);
		port = in.readUnsignedShort();
	}

}
