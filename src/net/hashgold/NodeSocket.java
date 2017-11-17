package net.hashgold;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

public class NodeSocket implements AutoCloseable{
	private final Socket _sock;
	private final int _hash_code;
	private final int _local_hash_code;
	private int _last_active_time;
	public final boolean isClient;
	
	NodeSocket(Socket sock, boolean client) throws SocketException {
		_sock= sock;
		_hash_code = _sock.getInetAddress().hashCode() ^ _sock.getPort();
		_local_hash_code = _sock.getLocalAddress().hashCode() ^ _sock.getLocalPort();
		isClient = client;
		_sock.setTcpNoDelay(true);
		_sock.setSoTimeout(Node.connect_timeout);//新连接响应超时时间,默认2秒
	}
	
	NodeSocket(Socket sock) throws SocketException {
		this(sock, false);
	}
	
	public void setSoTimeout(int seconds) throws SocketException {
		_sock.setSoTimeout(seconds * 1000);
	}
	
	public int hashCode() {
		return _hash_code;
	}
	
	public int localHashCode() {
		return _local_hash_code;
	}
	
	public boolean equals(NodeSocket sock) {
		return _sock.getInetAddress().equals(sock._sock.getInetAddress()) && _sock.getPort() == sock._sock.getPort();
	}
	
	public InputStream getInputStream() throws IOException {
		return _sock.getInputStream();
	}
	
	public OutputStream getOutputStream() throws IOException {
		return _sock.getOutputStream();
	}
	
	public void close() throws IOException {
		_sock.close();
	}
	
	
	public int getPort() {
		return _sock.getPort();
	}
	
	void touch() {
		_last_active_time = Node.getTimestamp();
	}
	
	int getActiveTime() {
		return _last_active_time;
	}
	
	public InetAddress getInetAddress() {
		return _sock.getInetAddress();
	}
}
