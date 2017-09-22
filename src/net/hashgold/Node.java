package net.hashgold;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.Date;
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

import collection.hashgold.LimitedRandomSet;
import exception.hashgold.ConnectionFull;
import exception.hashgold.UnrecognizedMessage;
import msg.hashgold.HeartBeat;
import msg.hashgold.Message;
import msg.hashgold.NodeDetection;
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
			logInfo("�յ���Ϣ"+_msg.getClass(),_sock);
			_msg.onReceive(new Responser(Node.this, _sock));

		}

	}
	
	
	/**
	 * �����¼�
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
					//�����ɿͻ��˷���
					if (entry.getValue() <= now - heart_beat_interval) {
						//logInfo("��������", entry.getKey());
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
						logInfo("����������", _sock);
						
						
						
						InputStream in = _sock.getInputStream();

						// ����Э��ͷ
						_sock.getOutputStream().write(HANDSHAKE_FLAG);
						
						// ��֤Э��ͷ
						byte[] buffer = new byte[HANDSHAKE_FLAG.length];
						in.read(buffer);
						//logInfo("Э��ͷ:"+new String(buffer));
						if (!MessageDigest.isEqual(buffer, HANDSHAKE_FLAG)) {
							_sock.close();
							logInfo("��ЧЭ��ͷ", _sock);
							return;
						}
						buffer = null;
						
						//�ͻ��˷�������
						if (_sock.isClient) {
							if (last_active_time == null) {
								// ��ʼ����
								last_active_time = new ConcurrentHashMap<NodeSocket, Integer>();
								logInfo("��ʼ������"+heart_beat_interval+ "��ÿ��");
								heart_beater = Executors.newSingleThreadScheduledExecutor();
								heart_beater.scheduleAtFixedRate(new HeartBeatEvent(), 0, 1, TimeUnit.SECONDS);
								sendTo(_sock, new HeartBeat());
								
							}
							
							last_active_time.put(_sock, getTimestamp());
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
							msg_type = in.read() & 0xff;
							msg_len = (in.read() << 8 + in.read()) & 0xffff;

							try {
								Message msg;
						
								msg = Registry.newMessageInstance(msg_type);
								
								msg.input(in, msg_len);
								
								// ����Ϣ�����̳߳ش���
								worker_pool.execute(new MessageCallback(msg, _sock));
								
								//�½ڵ�������ӳ�
								if (!node_added) {
									if (msg instanceof NodeDetection) {
										return;	
									}
									
									addConnected(_sock);
									logInfo("�����½ڵ�", _sock);
									
									node_added = true;
								}
								
								if (_sock.isClient) {
									last_active_time.put(_sock, getTimestamp());
								}
								
								
							} catch (InstantiationException | IllegalAccessException e) {
								e.printStackTrace();
								break;
							} catch (UnrecognizedMessage e) {
								//�޷�ʶ�����Ϣ,�Ͽ�����
								break;
							}

						} while (true);
						// <<<ѭ����ȡ��Ϣ

					} catch (IOException e) {
						// ��Ϣ��ȡ����
					}
					logInfo("�ڵ㱻�Ƴ�", _sock);
					delConnected(_sock);

				}
			});
			this.setDaemon(true);
		}

	}

	public static final byte[] HANDSHAKE_FLAG;// Э�����ֱ�ʶ

	public static final int heart_beat_interval = 15;// ���������,����һ���������δ�յ��Է���Ϣ����������һ������,����3���������ʱ������Ӧ���Ͽ�����
	
	public static int public_nodes_list_size = 500; //���湫���ڵ���������
	
	public int max_connections = 50;// �����������;

	public boolean debug = false;//������־
	

	static {
		HANDSHAKE_FLAG = "HASHGOLD".getBytes();
		
		// ע����Ϣ����
		Registry.registerMessage(new HeartBeat());// ����0
		Registry.registerMessage(new NodeDetection());// ̽��ڵ�1
	}

	private static int getTimestamp() {
		return (int) (System.currentTimeMillis() / 1000);
	}

	
	// >>>���Գ�ʼ��
	private ServerSocket sock_serv;// �������൥��

	private final CopyOnWriteArrayList<NodeSocket> connected_nodes;// ���ӽڵ�

	private Thread listen_thread;// �����߳�
	
	private final ThreadGroup message_loop_group;// ��Ϣѭ���߳���

	private ConcurrentHashMap<NodeSocket, Integer> last_active_time;// ����״̬,socket => ��������Ϣʱ��(��)

	
	private ScheduledExecutorService heart_beater;// ��������
	
	private final ExecutorService worker_pool;// ��Ϣ�����̳߳�

	private final NodeConnectedEvent connection_subscriber; //���Ӷ�����
	
	private final NodeDisconnectEvent disconnect_subscriber; //�Ͽ�������
	
	private  LimitedRandomSet<InetSocketAddress> public_nodes_list;	//�����ڵ��б�

	
	// <<<���Գ�ʼ��

	// >>>ʵ����������ģʽ,Server��Client

	
	public Node(NodeConnectedEvent on_connect, NodeDisconnectEvent on_disconnect) {
		connected_nodes = new CopyOnWriteArrayList<NodeSocket>();
		message_loop_group = new ThreadGroup("MESSAGE_LOOP");
		connection_subscriber = on_connect;
		disconnect_subscriber = on_disconnect;
		
		// ��ʼ�������̳߳�
		worker_pool = Executors.newCachedThreadPool();
		
		//�����ڵ��б�
		try {
			public_nodes_list = new LimitedRandomSet<InetSocketAddress>(public_nodes_list_size);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public Node() {
		this(null, null);
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
		if (connection_subscriber != null) {
			Thread t = new Thread() {
				public void run() {
					connection_subscriber.trigger(sock.getInetAddress(), sock.getPort(), Node.this);
				}
			};
			t.setDaemon(true);
			t.start();
		}
		
	}

	// <<<������ģʽ

	// >>>�ͻ���ģʽ
	
	/**
	 * ��ӹ����ڵ�
	 * @param addresses
	 */
	public void addPublicNodes(Set<InetSocketAddress> addresses) {
		public_nodes_list.add(addresses, new Predicate<InetSocketAddress>() {
			@Override
			public boolean test(InetSocketAddress socketAddr) {
				//�Խڵ��б����̽��
				return isInternetAddress(socketAddr.getAddress()) && detect(socketAddr.getAddress(), socketAddr.getPort());
			}});
	}
	
	
	/**
	 * ���һ��ip��ַ�Ƿ��ǻ������ɼ�
	 * @param addr
	 * @return
	 */
	private static boolean isInternetAddress(InetAddress addr) {
		return !(addr.isAnyLocalAddress() || 
				addr.isLinkLocalAddress() || 
				addr.isLoopbackAddress() ||
				addr.isMCGlobal() ||
				addr.isMCLinkLocal() ||
				addr.isMCNodeLocal() ||
				addr.isMCOrgLocal() ||
				addr.isMCSiteLocal() ||
				addr.isMulticastAddress() ||
				addr.isSiteLocalAddress()
				);
		
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
		int nSuccess = 0;
		for (NodeSocket sock : connected_nodes) {
			if (sendTo(sock, message)) {
				nSuccess++;
			}
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
	 */
	public void connect(InetAddress dest, int port) throws IOException, ConnectionFull {
		if (connected_nodes.size() >= max_connections) {
			throw new ConnectionFull("Max connection:" + max_connections);
		}
		NodeSocket sock = new NodeSocket(new Socket(dest, port), true);
		
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
				if (disconnect_subscriber != null) {
					Thread t = new Thread() {
						public void run() {
							disconnect_subscriber.trigger(sock.getInetAddress(), sock.getPort(), null);
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
	
	
	/**
	 * �ȴ�����������
	 */
	public void waitForServer() {
		if (listen_thread != null) {
			try {
				listen_thread.join();
			} catch (InterruptedException e) {
				//�������˳�
				shutdown();
			}
		}
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
	public void listen(int port, int backlog, InetAddress bindAddr) throws  IOException {
		sock_serv = new ServerSocket(port, backlog, bindAddr);
		// >>>�������̼߳�������
		listen_thread = new Thread() {
			public void run() {
				logInfo("��ʼ����,���ص�ַ:"+sock_serv.getInetAddress().getHostAddress() + ":" + sock_serv.getLocalPort());
				while (true) {
					
					if (this.isInterrupted()) {
						//�������ر�
						try {
							sock_serv.close();
						} catch (IOException e) {
						}
						logInfo("�����ر�");
						return;
					}
					if (connected_nodes.size() < max_connections) {
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

				}

				// �������쳣�����رսڵ�
				shutdown();
			}
		};
		listen_thread.setDaemon(true);
		listen_thread.start();
		// <<<�������̼߳�������
	}

	/**
	 * ������־
	 * @param string
	 */
	private void logInfo(String string) {
		if (debug) {
			System.out.println("["+new Date()+"] " + string);
		}
	}

	private void logInfo(String string, NodeSocket sock) {
		logInfo(string + ",Զ�̽ڵ�:"+sock.getInetAddress().getHostAddress() + ":"+sock.getPort());
	}
	
	
	/**
	 * ��һ���ڵ㷢����Ϣ
	 * 
	 * @param sock
	 * @param message
	 * @return ʧ��false
	 * @throws MessageTooLong
	 */
	boolean sendTo(NodeSocket sock, Message message) {
		logInfo("������Ϣ"+message.getClass(), sock);
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();

			message.output(out);

			OutputStream rawOut = sock.getOutputStream();
			rawOut.write(message.getType());

			int msg_len = out.size();
			if (msg_len > 65535) {
				System.err.println("Message type " + message.getType() + " too long");
				return false;
			}

			rawOut.write(message.getType());// ��Ϣ����
			rawOut.write((short) msg_len);// ��Ϣ����
			out.writeTo(rawOut);// ��Ϣ��
			rawOut.flush();
		} catch (IOException e) {
			e.printStackTrace();
			delConnected(sock);
			return false;
		}
		return true;
	}
	
	/**
	 * �رշ���
	 */
	public void shutdown() {
		logInfo("����ر�..");
		
		if (listen_thread != null) {
			try {
				listen_thread.interrupt();//���������߳�
				sock_serv.close();
			} catch (IOException e) {
			}
		}
		
		if (heart_beater != null) {
			heart_beater.shutdown();//ֹͣ����
		}
		

		worker_pool.shutdown();//���������̳߳�

		//�Ͽ����нڵ�
		for(NodeSocket sock :connected_nodes) {
			delConnected(sock);
		}
		
		logInfo("�ر����");
	}
	
	
	/**
	 * ̽��ڵ�
	 * @param addr
	 * @param port
	 * @return ����true
	 */
	public boolean detect(InetAddress addr, int port) {
		
		try (NodeSocket sock = new NodeSocket(new Socket(addr, port))){
			// ����Э��ͷ
			sock.getOutputStream().write(HANDSHAKE_FLAG);
			//����̽����Ϣ
			sendTo(sock, new NodeDetection());
			
			//��ȡ��Ӧ
			InputStream in = sock.getInputStream();
			
			// ��֤Э��ͷ
			byte[] buffer = new byte[HANDSHAKE_FLAG.length];
			in.read(buffer);
			if (!MessageDigest.isEqual(buffer, HANDSHAKE_FLAG)) {
				return false;
			}
			buffer = null;
			
			
			
			int msg_type = in.read() & 0xff;
			int msg_len = (in.read() << 8 + in.read()) & 0xffff;
			
			Message msg;
			msg = Registry.newMessageInstance(msg_type);
			msg.input(in, msg_len);
	
			return msg instanceof NodeDetection;

		} catch (Exception e) {
			return false;
		}
	}
}
