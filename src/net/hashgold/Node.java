package net.hashgold;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import collection.hashgold.BloomFilter;
import collection.hashgold.LimitedRandomSet;
import exception.hashgold.AlreadyConnected;
import exception.hashgold.ConnectionFull;
import exception.hashgold.NodesNotEnough;
import exception.hashgold.UnrecognizedMessage;
import msg.hashgold.ConnectionRefuse;
import msg.hashgold.ConnectivityDetectProxy;
import msg.hashgold.HeartBeat;
import msg.hashgold.Message;
import msg.hashgold.NewNodesShare;
import msg.hashgold.NodeDetection;
import msg.hashgold.NodesExchange;
import msg.hashgold.Registry;
import util.hashgold.WorkProof;
import util.hashgold.WorkProof.WorkResult;

public class Node {
	
	/**
	 * ����������������
	 * @author huangkaixuan
	 */
	private class ConcurrentWorkCalculator {
		private ReentrantLock lock;
		private byte[] source;
		private final int difficulity;
		private WorkProof[] workers;
		
		/**
		 * @param data ԭʼ����
		 * @param difficulity �����Ѷ�
		 */
		private ConcurrentWorkCalculator(byte[] data, int difficulity) {
			source = data;
			this.difficulity  = difficulity;
		}
		
		private void start() throws Exception {
			lock = new ReentrantLock();
			workers = new WorkProof[work_proof_thread_num];
			byte[] data;
			for(int i = 0; i < work_proof_thread_num; i++) {
				if (work_proof_thread_num > 1) {
					data = Arrays.copyOf(source, source.length + 1);
					data[source.length] = (byte)i;
				} else {
					data = source;
				}
				workers[i] = new WorkProof(data, difficulity, "MD5", true);	
				work_proof_generate_pool.execute(new WorkProofTask(this, i));
			}		
			
		}
		
		private void terminate() {
			for(WorkProof worker : workers) {
				worker.quit();
			}
		}
	}
	
	/**
	 * ��������������
	 * @author huangkaixuan
	 */
	private class WorkProofTask implements Runnable {
		private final ConcurrentWorkCalculator batch;
		private final WorkProof self_worker;
		
		/**
		 * @param to_thread �߳�����
		 * @param proof_workers
		 */
		private WorkProofTask(ConcurrentWorkCalculator batch, int index) {
			this.batch = batch;
			self_worker = batch.workers[index];
		}
		
		@Override
		public void run() {
			try {
				if (self_worker.startWork() &&  batch.lock.tryLock()) {
					//����ɹ��������
					batch.terminate();//���������̼߳���
					WorkResult result = self_worker.getResult();
					
					//������Ϣ,������������֤��
					byte[] toSend = Arrays.copyOf(result.source, result.source.length + 1 + result.nonce.length);
					if (work_proof_thread_num == 1) {
						toSend[result.source.length] = (byte)result.nonce.length;
					} else {
						//�����߳�index��nonce_size�ֽ�λ��
						toSend[result.source.length] = toSend[result.source.length - 1];
						toSend[result.source.length - 1] = (byte)(result.nonce.length + 1);
					}
					
					System.arraycopy(result.nonce, 0, toSend, result.source.length+1, result.nonce.length);
					logInfo("�㲥��������:" + Arrays.toString(toSend));
					floodAsync(toSend, null);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
	}
	
	/**
	 * ��Ϣ�ص�
	 * 
	 * @author huangkaixuan
	 *
	 */
	private class MessageCallback implements Runnable {
		private final Message _msg;
		private final NodeSocket _sock;
		private byte[] rawData;

		MessageCallback(Message message, NodeSocket sock, byte[] rawData) {
			_msg = message;
			_sock = sock;
			this.rawData = rawData;
		}

		@Override
		public void run() {
			logInfo("<<<--" + _msg.getClass(), _sock);
			_msg.onReceive(new Responser(Node.this, _sock, rawData));
			rawData = null;
	
			// �յ���Ϣ��������δ������Ƴ���ر�socket
			if (!connected_nodes.contains(_sock)) {
				try {
					if (_sock != null) {
						_sock.close();
					}
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
			int now = getTimestamp();
			for (NodeSocket sock : connected_nodes) {
				//����15��û�л(��/����Ϣ),�ͻ��˷���һ������������̽,������Ӻ�2��̽��
				if (sock.getActiveTime() <= now - heart_beat_interval - (sock.isClient ? 0:2)){
					sendTo(sock, new HeartBeat());
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
						DataInputStream in = new DataInputStream(new BufferedInputStream(_sock.getInputStream(),50));
						if (!in.markSupported()) {
							System.err.println("Message loop stream don't support mark");
							return;
						}
						
						// ����Э��ͷ
						_sock.getOutputStream().write(HANDSHAKE_FLAG);
						
						// ��֤Э��ͷ
						byte[] buffer = new byte[HANDSHAKE_FLAG.length];
						in.readFully(buffer);
						if (!Arrays.equals(buffer, HANDSHAKE_FLAG)) {
							_sock.close();
							logInfo("��ЧЭ��ͷ"+ new String(buffer), _sock);
							return;
						}
						buffer = null;

						// �ͻ��˷���ڵ㽻������
						if (_sock.isClient) {
								if (!sendTo(_sock, new NodesExchange(Node.this, 0))) {
									return;
								}
						}

						int msg_type;// ��Ϣ����:����0/̽��ڵ�1/�ڵ��б���2/�ܾ�����3/��ͨ�����5/�½ڵ㹲��6/�Զ�����Ϣ7
						byte[] netID = new byte[16];//����id
						int msg_len;// ��Ϣ����
						int msg_code = 0;//��Ϣ����
						boolean is_broadcast;//�Ƿ�㲥
						boolean node_added = false;//�ڵ��Ƿ��Ѿ���������
						int header_length;//��Ϣͷ����
						byte[] header_buffer =  new byte[25];//��Ϣͷ����
						byte[] buffer_total = null;//������Ϣ����
						byte[] nonce = null;
						MessageDigest md5_digest = MessageDigest.getInstance("MD5");
						
						//������Ϣѭ��
						do {
							header_length = 0;
							in.mark(25);		
							//��Ϣ����,�Ƿ�㲥
							msg_type = in.readUnsignedByte(); 
							is_broadcast = msg_type >= 0x80;//�Ƿ�㲥
							msg_type &= ~0x80;
							header_length++;
							
							
							if (msg_type == 7) {
								if (!node_added) {
									//δ���Ӳ������Զ�����Ϣ
									logInfo("�ܾ�δ�������ӽڵ��Զ�����Ϣ", _sock);
									break;
								}
							} else {
								if (msg_type > 7) {
									logInfo("��Ч��Ϣ����", _sock);
									break;
								}
							}
							
							if (msg_type == 7) {
								in.read(netID);//����id
								if (Arrays.equals(emptyNetID,netID)) {
									//�����粻�������Զ�����Ϣ
									logInfo("��Ч����", _sock);
									break;
								}
								msg_code = in.readUnsignedShort();//��Ϣ����
								header_length += 16 + 2;
							}
							
							msg_len = in.readUnsignedShort(); //��Ϣ����
							header_length += 2;
							
							//��ȡ��Ϣͷ
							if (is_broadcast) {
								in.readInt();
								header_length += 4;
								in.reset();
								in.read(header_buffer, 0, header_length);
							}
							
							//��ȡ��Ϣ��
							buffer = new byte[msg_len];
							in.readFully(buffer);
							
							if (is_broadcast) {
							//��ȡNonce
								int nonce_size = in.readUnsignedByte();
								if (nonce_size > 16) {
									break;
								}
								nonce = new byte[nonce_size];
								in.read(nonce);
							}
							// �������ӻʱ��
							_sock.touch();
							
							if (is_broadcast) {
								buffer_total = new byte[header_length + buffer.length +nonce.length];
								System.arraycopy(header_buffer, 0, buffer_total, 0, header_length);
								System.arraycopy(buffer, 0, buffer_total, header_length, msg_len);
								//buffer_total[header_length + buffer.length] = (byte)nonce.length;
								System.arraycopy(nonce, 0, buffer_total, header_length + buffer.length, nonce.length);
								
								//��鹤����
								int difficulity = WorkProof.getDifficulity(md5_digest.digest(buffer_total));
								if (difficulity < broadcast_difficulity) {
									logInfo("�㲥����������" + broadcast_difficulity );
									continue;
								}
								
								//�ع�������Ϣ,����nonce size
								buffer_total = new byte[buffer_total.length + 1];
								System.arraycopy(header_buffer, 0, buffer_total, 0, header_length);
								System.arraycopy(buffer, 0, buffer_total, header_length, msg_len);
								buffer_total[header_length + buffer.length] = (byte)nonce.length;
								System.arraycopy(nonce, 0, buffer_total, header_length + buffer.length + 1, nonce.length);
								//����ѭ����Ϣ
								if (!bloom_filter.add(buffer_total)) {
									//logInfo("��ֹ�ջ�������Ϣ",_sock);
									continue;
								}	
								
								logInfo("�յ��㲥,�Ѷ�:"+difficulity);
							}
							
							Message msg = null;
							try {
								// ��Ϣ����:����0/̽��ڵ�1/�ڵ��б���2/�ܾ�����3/��ͨ�����5/�½ڵ㹲��6/�Զ�����Ϣ7
								switch (msg_type) {
								case 0:
									msg = new HeartBeat();
									break;
								case 1:
									msg = new NodeDetection();
									break;
								case 2:
									msg = new NodesExchange();
									break;
								case 3:
									msg = new ConnectionRefuse();
									break;
								case 5:
									msg = new ConnectivityDetectProxy();
									break;
								case 6:
									msg = new NewNodesShare();
									break;
								case 7:
									if (!MessageDigest.isEqual(netID, Node.this.netID)) {
										//�Ǳ�������Ϣֱ��ת��
										if (is_broadcast) {
											floodAsync(buffer_total, _sock);
											buffer_total = null;
										}
										continue;
									}
									msg = Registry.newMessageInstance(msg_code);
									break;
								}
								
								//logInfo("�յ���Ϣ,��Ϣ����:"+buffer.length+ "�ֽ�" + " ��ǳ���:" + msg_len);
								msg.input(new DataInputStream(new ByteArrayInputStream(buffer)), msg_len);
								buffer = null;
								
								// ����δ����
								if (!node_added) {
									if (_sock.isClient) {
										if (msg instanceof NodesExchange) {// ��������������
											node_added = true;
										}
									} else {
										// �����
										if (msg instanceof NodesExchange) {
											if (connected_nodes.size() >= max_connections) {
												// �������������������
												NodesExchange nodesAvailable = new NodesExchange(Node.this, ((NodesExchange)msg).max_req);//�������ɿ��ýڵ���ͻ���
												nodesAvailable.max_req = 0;
												sendTo(_sock, new ConnectionRefuse("Connections are full", nodesAvailable));
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
									worker_pool.execute(new MessageCallback(msg, _sock, is_broadcast ? buffer_total:null));
								}
								buffer_total = null;

								// δ�������ӳػ��̱߳��ж�
								if (!node_added || Thread.currentThread().isInterrupted()) {
									return;
								}
								
							} catch (InstantiationException | IllegalAccessException e) {
								e.printStackTrace();
								break;
							} catch (UnrecognizedMessage e) {
								// �޷�ʶ�����Ϣ,�Ͽ�����
								logInfo("�޷�ʶ���������Ϣ", _sock);
								break;
							}
						} while (true);
						// <<<ѭ����ȡ��Ϣ

					} catch (Exception e) {
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

	private final static byte[] HANDSHAKE_FLAG;// Э�����ֱ�ʶ

	public static final int heart_beat_interval = 15;// �������,��,����һ�������������δ�����һ������

	public static int public_nodes_list_size = 500; // ���湫���ڵ���������

	public static int connect_timeout = 1500;// ���ӳ�ʱ,����

	public int max_connections = 50;// �����������;

	public boolean debug = false;// ������־

	private static final int bloom_filter_size;// ��¡�������ռ�
	
	private final byte[] netID;//�ڵ���������

	static int getTimestamp() {
		return (int) (System.currentTimeMillis() / 1000);
	}

	// >>>���Գ�ʼ��
	private ServerSocket sock_serv;// �������൥��

	private final CopyOnWriteArrayList<NodeSocket> connected_nodes;// ���ӽڵ�

	private final ThreadGroup message_loop_group;// ��Ϣѭ���߳���
														
	private ScheduledExecutorService heart_beater;// ��������

	private BloomFilter bloom_filter;// ��¡������

	private static final ExecutorService worker_pool;// ��Ϣ�����̳߳�

	private static ExecutorService work_proof_generate_pool;//������֤�������
	
	public NodeConnectedEvent onConnect; // ���Ӷ�����
	
	public PublicNodesFound onNodesFound;//�½ڵ㷢��
	
	public static int work_proof_thread_num = 4;//������֤���߳���
	
	public static int broadcast_difficulity = 21; //�㲥�Ѷ�,��ǰ������,�Ѷ�Ϊ21ʱ�ڵ�ÿ���ſ��Թ㲥8����Ϣ,����1���Ѷȷ���
	
	private Thread listen_thread;//�����߳�

	public NodeDisconnectEvent onDisconnect; // �Ͽ�������

	private LimitedRandomSet<InetSocketAddress> public_nodes_list; // �����ڵ��б�

	private static final ArrayList<InetAddress> local_addresses; // ����IP��ַ
	
	private static final byte[] emptyNetID;

	static {
		//������ID
		emptyNetID = new byte[16];
		
		// Э��ͷ
		HANDSHAKE_FLAG = "UnionP2P".getBytes();

		// ��¡��������С,Ĭ��1MB
		bloom_filter_size = 1024 * 1024 * 1;
		
		// ��ʼ�������̳߳�
		worker_pool = Executors.newCachedThreadPool();
		
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
	public Node(String network) {
		connected_nodes = new CopyOnWriteArrayList<NodeSocket>();
		message_loop_group = new ThreadGroup("MESSAGE_LOOP");

		

		// �����ڵ��б�
		try {
			bloom_filter = new BloomFilter(bloom_filter_size, 0.5);
			public_nodes_list = new LimitedRandomSet<InetSocketAddress>(public_nodes_list_size);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		if (network == null) {
			netID = emptyNetID;
		} else {
			MessageDigest digest = null;
			try {
				digest = MessageDigest.getInstance("MD5");
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			netID = digest.digest(network.getBytes());
		}
		
		logInfo("��ʼ����");
		heart_beater = Executors.newSingleThreadScheduledExecutor();
		heart_beater.scheduleAtFixedRate(new HeartBeatEvent(), 0, 1, TimeUnit.SECONDS);
	}
	

	/**
	 * ��ȡ�����ӽڵ���
	 * @return
	 */
	public int getConnectedNum() {
		return connected_nodes.size();
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
		sock.setSoTimeout(0);
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
	 * @param no_detection �������ͨ��
	 * @return 
	 */
	public Set<InetSocketAddress> addPublicNodes(Set<InetSocketAddress> addresses, boolean no_detection) {
			logInfo("���ڵ��б�..");
			return public_nodes_list.addAll(addresses, new Predicate<InetSocketAddress>() {
				@Override
				public boolean test(InetSocketAddress socketAddr) {
					// �Խڵ��б����̽��
					InetAddress addr = socketAddr.getAddress();
					return isOwnedAddress(addr) || !isInternetAddress(addr) || addr == getServerAddress() || !no_detection && !detect(addr, socketAddr.getPort());
				}
			});

	}
	
	
	/**
	 * ��Ӳ���Ⲣ�������ڵ��б�
	 * @param addresses
	 * @param from ��Դ,��Ϊnull
	 * @return 
	 */
	public void addAndSharePublicNodes(Set<InetSocketAddress> addresses,NodeSocket from, boolean dont_detect) {
		logInfo("��ӽڵ�..", from);
		Set<InetSocketAddress> newPublicAddresses = addPublicNodes(addresses, dont_detect);
		if (newPublicAddresses.size() > 0) {
			logInfo("ת���ڵ�..", from);
			if (onNodesFound != null) {
				onNodesFound.trigger(newPublicAddresses);
			}
			requestNeighbors(new NewNodesShare(newPublicAddresses), from, 0);
		}
	}

	/**
	 * ���һ��ip��ַ�Ƿ��ǻ������ɼ�
	 * 
	 * @param addr
	 * @return
	 */
	public static boolean isInternetAddress(InetAddress addr) {
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
	 * @throws Exception 
	 */
	public void broadcast(Message message, int min_connected) throws Exception {
		int have = getConnectedNum();
		if (have < min_connected) {
			throw new NodesNotEnough(min_connected, have);
		}
		
		//ʵ������������
		if (work_proof_generate_pool == null) {
			synchronized(getClass()) {
				if (work_proof_generate_pool == null) {
					work_proof_generate_pool = Executors.newFixedThreadPool(work_proof_thread_num);
				}
			}
		}
		
		//���м��㹤�������й㲥,20�����Լ�ٶ���1��5����Ϣ
		new ConcurrentWorkCalculator(packMessage(message,true, netID),broadcast_difficulity).start();
	}
	
	
	/**
	 * �㲥��Ϣ,��������һ���ڵ�
	 * @param message
	 * @throws Exception 
	 */
	public void broadcast(Message message) throws Exception {
			broadcast(message, 1);
	}

	
	/**
	 * ������ڽ��ڵ㷢������
	 * @param message ��Ϣ
	 * @param exclude �޳����ھ�
	 * @param limit �����ھ�����
	 * @return
	 */
	public int requestNeighbors(Message message, NodeSocket exclude,int limit) {
		return flood(packMessage(message, false,   netID), exclude, limit);
	}
	
	/**
	 * ��Ϣ����
	 * @param _msg
	 * @param exclude
	 * @param limit ���Ʒ��͸��ڵ�����
	 * @return
	 */
	int flood(byte[] _msg, NodeSocket exclude, int limit) {
		if (_msg == null) {
			return 0;
		}
		int nSuccess = 0;
		for (NodeSocket sock : connected_nodes) {
			if (exclude == null || !sock.equals(exclude)) {
				try {
					OutputStream out = sock.getOutputStream();
					out.write(_msg);
					out.flush();
					sock.touch();
					if (++nSuccess > limit && limit > 0) {
						break;
					}
				} catch (IOException e) {
					delConnected(sock);
				}
			}
		}
		logInfo("������Ϣ��" + nSuccess + "���ڵ�");

		return nSuccess;
	}
	
	/**
	 * �������������з���
	 * @param _msg
	 * @param exclude
	 * @return
	 */
	int flood(byte[] _msg, NodeSocket exclude) {
		return flood(_msg, exclude, 0);
	}
	
	
	/**
	 * �첽����
	 * @param _msg
	 * @param exclude
	 */
	private void floodAsync(byte[] _msg, NodeSocket exclude) {
		worker_pool.execute(new Runnable() {
			@Override
			public void run() {
				flood(_msg, exclude);
			}});
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
			if (connected_nodes.remove(sock)) {
				if (onDisconnect != null) {
					Thread t = new Thread() {
						public void run() {
							onDisconnect.trigger(sock.getInetAddress(), sock.getPort(), null);
						}
					};
					t.setDaemon(false);
					t.start();
				}
			}
			sock.close();
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
		if (sock_serv != null && sock_serv.isBound()) {
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
		if (sock_serv != null && sock_serv.isBound()) {
			return sock_serv.getLocalPort();
		}
		return 0;
	}

	/**
	 * ��������˿�
	 * 
	 * @throws UnknownHostException
	 * @throws DuplicateBinding
	 * @throws IOException
	 */
	public void listen(ListenSuccessEvent onSuccess) throws UnknownHostException, IOException {
		listen(0, 50, InetAddress.getByName("0.0.0.0"), onSuccess);
	}
	
	public void listen() throws UnknownHostException, IOException {
		listen(null);
	}

	// <<<�����ӿ�

	/**
	 * ����ָ���˿�
	 * 
	 * @param port
	 * @param onSuccess
	 * @throws IOException
	 * @throws DuplicateBinding
	 * @throws UnknownHostException
	 */
	public void listen(int port, ListenSuccessEvent onSuccess) throws UnknownHostException, IOException {
		listen(port, 50, InetAddress.getByName("0.0.0.0"), onSuccess);
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
	public void listen(int port, int backlog, InetAddress bindAddr, ListenSuccessEvent onSuccess) throws IOException {
		sock_serv = new ServerSocket(port, backlog, bindAddr);
		// >>>��ʼ��������

		logInfo("��ʼ����,���ص�ַ:" + sock_serv.getInetAddress().getHostAddress() + ":" + sock_serv.getLocalPort());
		
			listen_thread = new Thread() {
			public void run() {
				while (true) {
					try {
						NodeSocket sock = new NodeSocket(sock_serv.accept());
						// >>>������Ϣѭ���߳�
						new MessageLoopThread(sock).start();
						// <<<������Ϣѭ���߳�
					}catch (IOException e) {
						return;
					}
				}

				
			}
		};
		listen_thread.start();
		
		try {
			if (onSuccess != null) {
				if (onSuccess.trigger(this)) {
					listen_thread.join();
				}
			} else {
				listen_thread.join();
			}
		} catch (InterruptedException e) {
			//�ж�,��������
			logInfo("�жϼ���");
		}
		sock_serv.close();
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
		if (sock == null) {
			logInfo(string);
		} else {
			logInfo(string + ",Զ�̽ڵ�:" + sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
		}
		
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
			rawOut.write(packMessage(message, false, netID));
			rawOut.flush();
			sock.touch();
		} catch (IOException e) {
			e.printStackTrace();
			delConnected(sock);
			return false;
		}	
		return true;
	
	}

	private byte[] packMessage(Message message, boolean isBroadcast, byte[] netID) {
		ByteArrayOutputStream arr_out = new ByteArrayOutputStream();
		DataOutputStream data_arr_out = new DataOutputStream(arr_out);
		ByteArrayOutputStream arr_out_complete = new ByteArrayOutputStream();
		try {
			message.output(data_arr_out);// �����Ϣ��
			int msg_len = data_arr_out.size();// ȡ����Ϣ����
			if (msg_len > 65535) {
				System.err.println("Message type " + message.getCode() + " too long");
				return null;
			}
			int msg_code = message.getCode();// ��Ϣ����
			int msg_type = 7;
			if (message instanceof ConnectionRefuse ||
				message instanceof ConnectivityDetectProxy ||
				message instanceof HeartBeat ||
				message instanceof NewNodesShare ||
				message instanceof NodeDetection ||
				message instanceof NodesExchange 
					) {
				msg_type = msg_code;
			} 
			
			
			// ������װ��Ϣ
			data_arr_out = new DataOutputStream(arr_out_complete);
			data_arr_out.write(msg_type | (isBroadcast ? 0x80:0));
			if (msg_type == 7) {
				if (Arrays.equals(netID, emptyNetID)) {
					logInfo("���ʧ��,������");
					return null;
				}
				data_arr_out.write(netID);
				data_arr_out.writeShort(msg_code);
			}
			data_arr_out.writeShort(msg_len);
			if (isBroadcast) {
				data_arr_out.writeInt(new Random().nextInt(Integer.MAX_VALUE));//32λ���к����ֲ�ͬ��Ϣ
			}
			
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
		//��������
		if (listen_thread != null && listen_thread.isAlive()) {
			listen_thread.interrupt();
		}
		
		message_loop_group.interrupt();//�ر���Ϣѭ��
		
		if (heart_beater != null) {
			heart_beater.shutdown();// ֹͣ����
		}
		
		worker_pool.shutdown();// ���������̳߳�
		
		if (work_proof_generate_pool != null) {
			work_proof_generate_pool.shutdownNow();
		}
		
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
			in.readFully(buffer);
			if (!MessageDigest.isEqual(buffer, HANDSHAKE_FLAG)) {
				return false;
			}

			int msg_type = in.readUnsignedByte();
			
			if (msg_type != 1) {
				return false;
			}
			logInfo(addr.getHostAddress() + "������");
			return true;
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
