package net.hashgold;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import collection.hashgold.BloomFilter;
import collection.hashgold.LimitedRandomSet;
import exception.hashgold.AlreadyConnected;
import exception.hashgold.ConnectionFull;
import exception.hashgold.DuplicateMessageNumber;
import exception.hashgold.UnrecognizedMessage;
import msg.hashgold.ConnectionRefuse;
import msg.hashgold.HeartBeat;
import msg.hashgold.HelloWorld;
import msg.hashgold.Message;
import msg.hashgold.NodeDetection;
import msg.hashgold.NodesExchange;
import msg.hashgold.Registry;

public class Node {
	/**
	 * ��Ϣ�ص�
	 * 
	 * @author huangkaixuan
	 *
	 */
	private class MessageCallback implements Runnable {
		private Message _msg;
		private NodeSocket _sock;

		MessageCallback(Message message, NodeSocket sock) {
			_msg = message;
			_sock = sock;
		}

		@Override
		public void run() {
			logInfo("<<<--" + _msg.getClass(), _sock);
			_msg.onReceive(new Responser(Node.this, _sock, _msg));

			// �յ���Ϣ��������δ������Ƴ���ر�socket
			if (!connected_nodes.contains(_sock)) {
				try {
					_sock.close();
				} catch (IOException e) {
				}
			}

		}

	}

	/**
	 * �����¼�
	 * 
	 * @author huangkaixuan
	 *
	 */
	private class HeartBeatEvent implements Runnable {
		public void run() {
			Iterator<Entry<NodeSocket, Integer>> it = last_active_time.entrySet().iterator();
			int now = getTimestamp();
			Entry<NodeSocket, Integer> entry;
			while (it.hasNext()) {
				entry = it.next();
				// �����ɿͻ��˷���
				if (entry.getValue() <= now - heart_beat_interval) {
					// logInfo("��������", entry.getKey());
					sendTo(entry.getKey(), new HeartBeat());
				}
			}
		}

	}

	/**
	 * ��Ϣѭ���߳�
	 * 
	 * @author huangkaixuan
	 *
	 */
	private class MessageLoopThread extends Thread {
		MessageLoopThread(NodeSocket _sock) {
			super(message_loop_group, new Runnable() {
				public void run() {
					try {
						// logInfo("����������", _sock);

						DataInputStream in = new DataInputStream(_sock.getInputStream());
						// ����Э��ͷ
						_sock.getOutputStream().write(HANDSHAKE_FLAG);

						// ��֤Э��ͷ
						byte[] buffer = new byte[HANDSHAKE_FLAG.length];
						in.read(buffer);
						// logInfo("Э��ͷ:"+new String(buffer));
						if (!MessageDigest.isEqual(buffer, HANDSHAKE_FLAG)) {
							_sock.close();
							logInfo("��ЧЭ��ͷ", _sock);
							return;
						}
						buffer = null;

						// �ͻ��˷���ڵ㽻������
						if (_sock.isClient) {
							try {
								sendTo(_sock, new NodesExchange(Node.this, 0));
							} catch (Exception e) {
								e.printStackTrace();
							}
						}

						// >>>ѭ����ȡ��Ϣ

						/**
						 * ��Ϣ��ʽ,����Ķ����޷����� =========================== ||1B ��Ϣ����|2B ��Ϣ����|NB ����||
						 * ===========================
						 */

						int msg_type;// ��Ϣ����
						int msg_len;// ��Ϣ����
						boolean node_added = false;

						do {
							msg_type = in.readUnsignedByte();
							msg_len = in.readUnsignedShort();
							try {
								Message msg;

								msg = Registry.newMessageInstance(msg_type);

								msg.input(in, msg_len);
								// logInfo("��Ϣ����" + msg_len, _sock);

								// �յ���һ����Ϣ
								if (!node_added) {
									if (_sock.isClient) {
										// �ͻ���

										if (!(msg instanceof ConnectionRefuse)) {// ������δ�ܾ�
											node_added = true;
										}
									} else {
										// �����
										if (!(msg instanceof NodeDetection)) {
											if (connected_nodes.size() >= max_connections) {
												// ����Ƿ񳬹����������������
												sendTo(_sock, new ConnectionRefuse("Connections are full"));
												logInfo("�������������" + max_connections, _sock);
											} else {
												// ����������
												node_added = true;
											}
										}

									}

									if (node_added) {
										addConnected(_sock);
										logInfo("�����½ڵ�", _sock);
									}
								}

								// ֻ�н��ܵ����ӻ���̽��;ܾ���Ϣ������
								if (node_added || msg instanceof NodeDetection || msg instanceof ConnectionRefuse) {
									worker_pool.execute(new MessageCallback(msg, _sock));
								}

								// ���·�������Ӧʱ��
								if (node_added && _sock.isClient) {
									last_active_time.put(_sock, getTimestamp());
								}

								// δ�������ӳ����˳���Ϣѭ��
								if (!node_added) {
									return;
								}
							} catch (InstantiationException | IllegalAccessException e) {
								e.printStackTrace();
								break;
							} catch (UnrecognizedMessage e) {
								// �޷�ʶ�����Ϣ,�Ͽ�����
								logInfo("�޷�ʶ����Ϣ", _sock);
								break;
							}

						} while (true);
						// <<<ѭ����ȡ��Ϣ

					} catch (IOException e) {
						// ��Ϣ��ȡ����
						if (debug) {
							e.printStackTrace();
						}
					}
					logInfo("�ڵ㱻�Ƴ�", _sock);
					delConnected(_sock);

				}
			});
			this.setDaemon(true);
		}

	}

	public static byte[] HANDSHAKE_FLAG;// Э�����ֱ�ʶ

	public static final int heart_beat_interval = 15;// ���������,����һ���������δ�յ��Է���Ϣ����������һ������,����3���������ʱ������Ӧ���Ͽ�����

	public static int public_nodes_list_size = 500; // ���湫���ڵ���������

	public static int connect_timeout = 500;// ���ӳ�ʱ,����

	public int max_connections = 50;// �����������;

	public boolean debug = false;// ������־

	private static final int bloom_filter_size;// ��¡�������ռ�

	static {
		// Э��ͷ
		HANDSHAKE_FLAG = "HASHGOLD".getBytes();

		// ��¡��������С,Ĭ��4MB
		bloom_filter_size = 1024 * 1024 * 4;

		// ע����Ϣ����
		try {
			Registry.registerMessage(new HeartBeat());// ����0
			Registry.registerMessage(new NodeDetection());// ̽��ڵ�1
			Registry.registerMessage(new NodesExchange());// �ڵ��б���2
			Registry.registerMessage(new ConnectionRefuse());// �ܾ�����3
			Registry.registerMessage(new HelloWorld());// �ʺ����4
		} catch (DuplicateMessageNumber e) {
			e.printStackTrace();
		}

	}

	private static int getTimestamp() {
		return (int) (System.currentTimeMillis() / 1000);
	}

	// >>>���Գ�ʼ��
	private ServerSocket sock_serv;// �������൥��

	private final CopyOnWriteArrayList<NodeSocket> connected_nodes;// ���ӽڵ�

	private final ThreadGroup message_loop_group;// ��Ϣѭ���߳���

	private ConcurrentHashMap<NodeSocket, Integer> last_active_time;// ����״̬,socket
																	// =>
																	// ��������Ϣʱ��(��)

	private ScheduledExecutorService heart_beater;// ��������

	private BloomFilter bloom_filter;// ��¡������

	private final ExecutorService worker_pool;// ��Ϣ�����̳߳�

	public NodeConnectedEvent onConnect; // ���Ӷ�����

	public NodeDisconnectEvent onDisconnecct; // �Ͽ�������

	private LimitedRandomSet<InetSocketAddress> public_nodes_list; // �����ڵ��б�

	private static final ArrayList<InetAddress> local_addresses; // ����IP��ַ

	static {
		// ��ȡ������ַ
		local_addresses = new ArrayList<InetAddress>(5);
		InetAddress addr;
		Enumeration<NetworkInterface> allNetInterfaces;
		try {
			allNetInterfaces = NetworkInterface.getNetworkInterfaces();
			while (allNetInterfaces.hasMoreElements()) {
				NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();

				Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
				while (addresses.hasMoreElements()) {
					addr = (InetAddress) addresses.nextElement();
					if (addr != null && isInternetAddress(addr)) {
						local_addresses.add(addr);
					}
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}

	// <<<���Գ�ʼ��

	// >>>ʵ����������ģʽ,Server��Client
	public Node() {
		connected_nodes = new CopyOnWriteArrayList<NodeSocket>();
		message_loop_group = new ThreadGroup("MESSAGE_LOOP");

		// ��ʼ�������̳߳�
		worker_pool = Executors.newCachedThreadPool();

		// �����ڵ��б�
		try {
			bloom_filter = new BloomFilter(bloom_filter_size, 0.5);
			public_nodes_list = new LimitedRandomSet<InetSocketAddress>(public_nodes_list_size);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * ��ȡ���ֹ����ڵ�
	 * 
	 * @param n
	 * @return
	 */
	public Set<InetSocketAddress> getPublicNodes(int n) {
		if (n == 0) {
			return null;
		}
		try {
			return public_nodes_list.pick(n);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * ��ȡȫ�������ڵ�
	 * 
	 * @return
	 */
	public LimitedRandomSet<InetSocketAddress> getPublicNodesList() {
		return public_nodes_list;
	}

	/**
	 * ������ӽڵ�
	 * 
	 * @param sock
	 * @throws SocketException
	 */
	private void addConnected(NodeSocket sock) throws SocketException {
		sock.setSoTimeout(heart_beat_interval * 3);
		connected_nodes.add(sock);
		if (onConnect != null) {
			Thread t = new Thread() {
				public void run() {
					onConnect.trigger(sock.getInetAddress(), sock.getPort(), Node.this);
				}
			};
			t.setDaemon(true);
			t.start();
		}

	}

	// <<<������ģʽ

	// >>>�ͻ���ģʽ

	/**
	 * ��ӹ����ڵ� ����½ڵ��Զ�̽�������Ч�ڵ�
	 * 
	 * @param addresses
	 */
	public void addPublicNodes(Set<InetSocketAddress> addresses) {
		logInfo("���ڵ��б�..");
		public_nodes_list.addAll(addresses, new Predicate<InetSocketAddress>() {
			@Override
			public boolean test(InetSocketAddress socketAddr) {
				// �Խڵ��б����̽��
				InetAddress addr = socketAddr.getAddress();
				return isOwnedAddress(addr) || !isInternetAddress(addr) || !detect(addr, socketAddr.getPort());
			}
		});
		logInfo("������,�б���" + public_nodes_list.size());
	}

	/**
	 * ���һ��ip��ַ�Ƿ��ǻ������ɼ�
	 * 
	 * @param addr
	 * @return
	 */
	private static boolean isInternetAddress(InetAddress addr) {
		return !(addr.isAnyLocalAddress() || addr.isLinkLocalAddress() || addr.isLoopbackAddress() || addr.isMCGlobal()
				|| addr.isMCLinkLocal() || addr.isMCNodeLocal() || addr.isMCOrgLocal() || addr.isMCSiteLocal()
				|| addr.isMulticastAddress() || addr.isSiteLocalAddress());

	}

	/**
	 * ����Ƿ񱾻���ַ
	 * 
	 * @param addr
	 * @return
	 */
	private static boolean isOwnedAddress(InetAddress addr) {
		return local_addresses.contains(addr);
	}

	/**
	 * �㲥��Ϣ
	 * 
	 * @param type
	 *            ��Ϣ����
	 * @param buffer
	 *            ��Ϣ����
	 * @return �ɹ����͵����ٽڵ�
	 */
	public int broadcast(Message message) {
		return flood(message, null);
	}

	/**
	 * ת������source����Ϣ
	 * 
	 * @param message
	 * @param source
	 * @return
	 */
	int forward(Message message, NodeSocket source) {
		return flood(message, source);
	}
	
	
	/**
	 * ��Ϣ����
	 * @param message
	 * @param exclude
	 * @return
	 */
	private int flood(Message message, NodeSocket exclude) {
		int nSuccess = 0;
		byte[] data_pack = packMessage(message);
		if (bloom_filter.add(data_pack)) {
			for (NodeSocket sock : connected_nodes) {
				if (!sock.equals(exclude)) {
					try {
						OutputStream out = sock.getOutputStream();
						out.write(data_pack);
						out.flush();
						nSuccess++;
					} catch (IOException e) {
						delConnected(sock);
					}
				}
			}
		} else {
			logInfo("��ֹ�ظ�ת����Ϣ"+message.getClass(), exclude);
		}
		return nSuccess;
	}
	
	

	// <<<�ͻ���ģʽ

	// <<<ʵ����������ģʽ,Server��Client

	// >>>�����ӿ�

	/**
	 * ��Ϊ�ͻ�����������
	 * 
	 * @param dest
	 * @param port
	 * @throws IOException
	 * @throws ConnectionFull
	 * @throws AlreadyConnected 
	 */
	synchronized public void connect(InetAddress dest, int port) throws IOException, ConnectionFull, AlreadyConnected {
		if (connected_nodes.size() >= max_connections) {
			throw new ConnectionFull("Max connection:" + max_connections);
		}

		// ��ʼ����
		if (last_active_time == null) {
			last_active_time = new ConcurrentHashMap<NodeSocket, Integer>();
			logInfo("��ʼ������" + heart_beat_interval + "��ÿ��");
			heart_beater = Executors.newSingleThreadScheduledExecutor();
			heart_beater.scheduleAtFixedRate(new HeartBeatEvent(), 0, 1, TimeUnit.SECONDS);
		}
		Socket _sock = new Socket();
		_sock.connect(new InetSocketAddress(dest, port), connect_timeout);
		NodeSocket sock = new NodeSocket(_sock, true);
		if (connected_nodes.contains(sock)) {
			sock.close();
			throw new AlreadyConnected(sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
		}

		new MessageLoopThread(sock).start();// ������Ϣѭ��
		logInfo("��������", sock);

	}

	/**
	 * �Ƴ��ڵ�
	 * 
	 * @param sock
	 */
	void delConnected(NodeSocket sock) {
		try {
			sock.close();
			if (connected_nodes.remove(sock)) {
				if (onDisconnecct != null) {
					Thread t = new Thread() {
						public void run() {
							onDisconnecct.trigger(sock.getInetAddress(), sock.getPort(), null);
						}
					};
					t.setDaemon(true);
					t.start();
				}
			}
			last_active_time.remove(sock);
		} catch (Exception e) {
		}

	}

	public void finalize() {
		shutdown();
	}

	/**
	 * ��ȡ���ط�������ַ
	 * 
	 * @return
	 */
	public InetAddress getServerAddress() {
		if (sock_serv.isBound()) {
			return sock_serv.getInetAddress();
		}
		return null;
	}

	/**
	 * ��ȡ���ط������˿�
	 * 
	 * @return
	 */
	public int getServerPort() {
		if (sock_serv.isBound()) {
			return sock_serv.getLocalPort();
		}
		return -1;
	}

	/**
	 * ��������˿�
	 * 
	 * @throws UnknownHostException
	 * @throws DuplicateBinding
	 * @throws IOException
	 */
	public void listen() throws UnknownHostException, IOException {
		listen(0, 50, InetAddress.getByName("0.0.0.0"));
	}

	// <<<�����ӿ�

	/**
	 * ����ָ���˿�
	 * 
	 * @param port
	 * @throws IOException
	 * @throws DuplicateBinding
	 * @throws UnknownHostException
	 */
	public void listen(int port) throws UnknownHostException, IOException {
		listen(port, 50, InetAddress.getByName("0.0.0.0"));
	}

	// >>>������ģʽ
	/**
	 * ��Ϊ�����������˿�
	 * 
	 * @param port
	 * @param backlog
	 * @param bindAddr
	 * @throws DuplicateBinding
	 * @throws IOException
	 */
	public void listen(int port, int backlog, InetAddress bindAddr) throws IOException {
		sock_serv = new ServerSocket(port, backlog, bindAddr);
		// >>>��ʼ��������

		logInfo("��ʼ����,���ص�ַ:" + sock_serv.getInetAddress().getHostAddress() + ":" + sock_serv.getLocalPort());
		while (true) {
			if (Thread.currentThread().isInterrupted()) {
				// �������ر�
				try {
					sock_serv.close();
				} catch (IOException e) {
				}
				logInfo("�����ر�");
				return;
			}

			try {
				NodeSocket sock = new NodeSocket(sock_serv.accept());
				// >>>������Ϣѭ���߳�
				new MessageLoopThread(sock).start();
				// <<<������Ϣѭ���߳�
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}
		}

		// �������쳣�����رսڵ�
		shutdown();

		// <<<��ʼ��������
	}

	/**
	 * ������־
	 * 
	 * @param string
	 */
	private void logInfo(String string) {
		if (debug) {
			System.out.println("[" + new Date() + "] " + string);
		}
	}

	private void logInfo(String string, NodeSocket sock) {
		logInfo(string + ",Զ�̽ڵ�:" + sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
	}

	/**
	 * ��һ���ڵ㷢����Ϣ
	 * 
	 * @param sock
	 * @param message
	 * @param isForward
	 *            �Ƿ�ת��
	 * @return ʧ��false
	 * @throws MessageTooLong
	 */
	boolean sendTo(NodeSocket sock, Message message) {
		logInfo(message.getClass() + "--->>>", sock);
		try {
			OutputStream rawOut = sock.getOutputStream();
			rawOut.write(packMessage(message));
			rawOut.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}	
		return true;
	
	}

	private byte[] packMessage(Message message) {
		ByteArrayOutputStream arr_out = new ByteArrayOutputStream();
		DataOutputStream data_arr_out = new DataOutputStream(arr_out);
		ByteArrayOutputStream arr_out_complete = new ByteArrayOutputStream();
		try {
			message.output(data_arr_out);// �����Ϣ��
			int msg_len = data_arr_out.size();// ȡ����Ϣ����
			if (msg_len > 65535) {
				System.err.println("Message type " + message.getType() + " too long");
				return null;
			}
			int msg_type = message.getType();// ��Ϣ����

			// ������װ��Ϣ
			data_arr_out = new DataOutputStream(arr_out_complete);
			data_arr_out.write(msg_type);
			data_arr_out.writeShort(msg_len);

			arr_out.writeTo(arr_out_complete);
		} catch (IOException e) {

			e.printStackTrace();
			return null;
		}
		return arr_out_complete.toByteArray();
	}


	/**
	 * �رշ���
	 */
	public void shutdown() {
		logInfo("����ر�..");
		if (heart_beater != null) {
			heart_beater.shutdown();// ֹͣ����
		}

		worker_pool.shutdown();// ���������̳߳�

		// �Ͽ����нڵ�
		for (NodeSocket sock : connected_nodes) {
			delConnected(sock);
		}

		logInfo("�ر����");
	}

	/**
	 * ̽��ڵ�
	 * 
	 * @param addr
	 * @param port
	 * @return ����true
	 */
	public boolean detect(InetAddress addr, int port) {
		logInfo(addr.getHostAddress() + "��ʼ���");

		NodeSocket sock = null;
		try {
			Socket _sock = new Socket();
			_sock.connect(new InetSocketAddress(addr, port), connect_timeout);
			sock = new NodeSocket(_sock);
			// ����Э��ͷ
			sock.getOutputStream().write(HANDSHAKE_FLAG);
			// ����̽����Ϣ
			sendTo(sock, new NodeDetection());

			// ��ȡ��Ӧ
			DataInputStream in = new DataInputStream(sock.getInputStream());

			// ��֤Э��ͷ
			byte[] buffer = new byte[HANDSHAKE_FLAG.length];
			in.read(buffer);
			if (!MessageDigest.isEqual(buffer, HANDSHAKE_FLAG)) {
				return false;
			}
			buffer = null;

			int msg_type = in.readUnsignedByte();
			int msg_len = in.readUnsignedShort();

			Message msg;
			msg = Registry.newMessageInstance(msg_type);
			msg.input(in, msg_len);
			logInfo(addr.getHostAddress() + "������");
			return msg instanceof NodeDetection;

		} catch (Exception e) {
			if (debug) {
				e.printStackTrace();
			}
			logInfo(addr.getHostAddress() + "���ʧ��");
			return false;
		} finally {
			try {
				if (sock != null) {
					sock.close();
				}
			} catch (IOException e) {
			}
		}
	}
}
