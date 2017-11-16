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
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Random;
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
import exception.hashgold.UnrecognizedMessage;
import msg.hashgold.ConnectionRefuse;
import msg.hashgold.ConnectivityDetectProxy;
import msg.hashgold.HeartBeat;
import msg.hashgold.Message;
import msg.hashgold.NewNodesShare;
import msg.hashgold.NodeDetection;
import msg.hashgold.NodesExchange;
import msg.hashgold.Registry;

public class Node {
	/**
	 * 消息回调
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
	
			// 收到消息后发现连接未加入或被移除则关闭socket
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
	 * 心跳事件
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
				// 心跳由客户端发起
				if (entry.getValue() <= now - heart_beat_interval) {
					// logInfo("发送心跳", entry.getKey());
					sendTo(entry.getKey(), new HeartBeat());
			
				}
			}
		}

	}

	/**
	 * 消息循环线程
	 * 
	 * @author huangkaixuan
	 *
	 */
	private class MessageLoopThread extends Thread {
		MessageLoopThread(NodeSocket _sock) {
			super(message_loop_group, new Runnable() {
				public void run() {
					try {
						DataInputStream in = new DataInputStream(new BufferedInputStream(_sock.getInputStream(), 50));
						if (!in.markSupported()) {
							System.err.println("Message loop stream don't support mark");
							return;
						}
						
						// 发送协议头
						_sock.getOutputStream().write(HANDSHAKE_FLAG);
						
						// 验证协议头
						byte[] buffer = new byte[HANDSHAKE_FLAG.length];
						in.readFully(buffer);
						if (!Arrays.equals(buffer, HANDSHAKE_FLAG)) {
							_sock.close();
							logInfo("无效协议头"+ new String(buffer), _sock);
							return;
						}
						buffer = null;

						// 客户端发起节点交换请求
						if (_sock.isClient) {
								if (!sendTo(_sock, new NodesExchange(Node.this, 0))) {
									return;
								}
						}

						int msg_type;// 消息类型:心跳0/探测节点1/节点列表交换2/拒绝连接3/连通代检测5/新节点共享6/自定义消息7
						byte[] netID = new byte[16];//网络id
						int msg_len;// 消息长度
						int msg_code = 0;//消息代码
						boolean is_flood;//是否泛洪
						boolean node_added = false;//节点是否已经建立连接
						int header_length;//消息头长度
						byte[] header_buffer =  new byte[25];//消息头缓存
						byte[] buffer_total = null;//完整消息缓存
						
						//进入消息循环
						do {
							header_length = 0;
							in.mark(25);		
							//消息类型,是否泛洪		
							msg_type = in.readUnsignedByte(); 
							is_flood = msg_type >= 0x80;//是否泛洪
							msg_type &= ~0x80;
							header_length++;
							
							
							if (msg_type == 7) {
								if (!node_added) {
									//未连接不接收自定义消息
									logInfo("拒绝未建立连接节点自定义消息", _sock);
									break;
								}
							} else {
								if (msg_type > 7) {
									logInfo("无效消息类型", _sock);
									break;
								}
							}
							
							if (msg_type == 7) {
								in.read(netID);//网络id
								if (Arrays.equals(emptyNetID,netID)) {
									//空网络不允许发送自定义消息
									logInfo("无效网络", _sock);
									break;
								}
								msg_code = in.readUnsignedShort();//消息代码
								header_length += 16 + 2;
							}
							
							msg_len = in.readUnsignedShort(); //消息长度
							header_length += 2;
							
							if (is_flood) {
								in.readInt();//消息序列号
								header_length += 4;
								in.reset();
								in.read(header_buffer, 0, header_length);
							}
							
							//读取消息体
							buffer = new byte[msg_len];
							in.readFully(buffer);
							
							// 更新服务器响应时间
							if (node_added && _sock.isClient) {
								last_active_time.put(_sock, getTimestamp());
							}
							
							
							if (is_flood) {
								//过滤闭环的泛洪消息
								buffer_total = new byte[header_length + buffer.length];
								System.arraycopy(header_buffer, 0, buffer_total, 0, header_length);
								System.arraycopy(buffer, 0, buffer_total, header_length, msg_len);
								if (!bloom_filter.add(buffer_total)) {
									logInfo("阻止闭环泛洪消息",_sock);
									continue;
								}	
							}
							
							Message msg = null;
							try {
								// 消息类型:心跳0/探测节点1/节点列表交换2/拒绝连接3/连通代检测5/新节点共享6/自定义消息7
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
										//非本网络消息直接转发
										if (is_flood) {
											flood(buffer_total, _sock);
										}
										buffer_total = null;
										continue;
									}
									msg = Registry.newMessageInstance(msg_code);
									break;
								}
								
								//logInfo("收到消息,消息长度:"+buffer.length+ "字节" + " 标记长度:" + msg_len);
								msg.input(new DataInputStream(new ByteArrayInputStream(buffer)), msg_len);
								buffer = null;
								
								// 连接未建立
								if (!node_added) {
									if (_sock.isClient) {
										if (msg instanceof NodesExchange) {// 服务器接受连接
											node_added = true;
										}
									} else {
										// 服务端
										if (msg instanceof NodesExchange) {
											if (connected_nodes.size() >= max_connections) {
												// 超过服务器最大连接数
												NodesExchange nodesAvailable = new NodesExchange(Node.this, ((NodesExchange)msg).max_req);//返回若干可用节点给客户端
												nodesAvailable.max_req = 0;
												sendTo(_sock, new ConnectionRefuse("Connections are full", nodesAvailable));
												logInfo("超过最大连接数" + max_connections, _sock);
											} else {
												// 接受新连接
												node_added = true;
											}
										}

									}

									if (node_added) {
										
										addConnected(_sock);
										
										// 初始化服务器响应时间
										if ( _sock.isClient) {
											last_active_time.put(_sock, getTimestamp());
										}
										logInfo("加入新节点", _sock);
									}
								}

								// 只有接受的连接或者探测和拒绝消息被处理
								if (node_added || msg instanceof NodeDetection || msg instanceof ConnectionRefuse) {
									worker_pool.execute(new MessageCallback(msg, _sock, is_flood ? Arrays.copyOf(buffer_total, buffer_total.length):null));
								}
								buffer_total = null;

								// 未加入连接池或线程被中断
								if (!node_added || Thread.currentThread().isInterrupted()) {
									return;
								}
								
							} catch (InstantiationException | IllegalAccessException e) {
								e.printStackTrace();
								break;
							} catch (UnrecognizedMessage e) {
								// 无法识别的消息,断开连接
								logInfo("无法识别的网络消息", _sock);
								break;
							}
						} while (true);
						// <<<循环读取消息

					} catch (Exception e) {
						// 消息读取出错
						if (debug) {
							e.printStackTrace();
						}
					}
					logInfo("节点被移除", _sock);
					delConnected(_sock);

				}
			});
			this.setDaemon(true);
		}

	}

	private final static byte[] HANDSHAKE_FLAG;// 协议握手标识

	public static final int heart_beat_interval = 15;// 心跳间隔秒,超过一个心跳间隔未收到对方消息则主动发出一个心跳,超过3个心跳间隔时间无响应将断开连接

	public static int public_nodes_list_size = 500; // 保存公共节点数量限制

	public static int connect_timeout = 1500;// 连接超时,毫秒

	public int max_connections = 50;// 最大连接数量;

	public boolean debug = false;// 调试日志

	private static final int bloom_filter_size;// 布隆过滤器空间
	
	private final byte[] netID;//节点所属网络

	private static int getTimestamp() {
		return (int) (System.currentTimeMillis() / 1000);
	}

	// >>>属性初始化
	private ServerSocket sock_serv;// 服务器类单例

	private final CopyOnWriteArrayList<NodeSocket> connected_nodes;// 连接节点

	private final ThreadGroup message_loop_group;// 消息循环线程组

	private ConcurrentHashMap<NodeSocket, Integer> last_active_time;// 心跳状态,socket
																	// =>
																	// 最后接收消息时间(秒)

	private ScheduledExecutorService heart_beater;// 心跳起搏器

	private BloomFilter bloom_filter;// 布隆过滤器

	private final ExecutorService worker_pool;// 消息处理线程池

	public NodeConnectedEvent onConnect; // 连接订阅者
	
	public PublicNodesFound onNodesFound;//新节点发现
	
	private Thread listen_thread;//监听线程

	public NodeDisconnectEvent onDisconnect; // 断开订阅者

	private LimitedRandomSet<InetSocketAddress> public_nodes_list; // 公共节点列表

	private static final ArrayList<InetAddress> local_addresses; // 本地IP地址
	
	private static final byte[] emptyNetID;

	static {
		//空网络ID
		emptyNetID = new byte[16];
		
		// 协议头
		HANDSHAKE_FLAG = "UnionP2P".getBytes();

		// 布隆过滤器大小,默认1MB
		bloom_filter_size = 1024 * 1024 * 1;
				
		// 获取本机地址
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

	// <<<属性初始化

	// >>>实现两种运行模式,Server、Client
	public Node(String network) {
		connected_nodes = new CopyOnWriteArrayList<NodeSocket>();
		message_loop_group = new ThreadGroup("MESSAGE_LOOP");

		// 初始化工作线程池
		worker_pool = Executors.newCachedThreadPool();

		// 公共节点列表
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
	}
	

	/**
	 * 获取已连接节点数
	 * @return
	 */
	public int getConnectedNum() {
		return connected_nodes.size();
	}
	
	
	/**
	 * 获取部分公共节点
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
	 * 获取全部公共节点
	 * 
	 * @return
	 */
	public LimitedRandomSet<InetSocketAddress> getPublicNodesList() {
		return public_nodes_list;
	}

	/**
	 * 添加连接节点
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

	// <<<服务器模式

	// >>>客户端模式

	/**
	 * 添加公共节点 会对新节点自动探测过滤无效节点
	 * 
	 * @param addresses
	 * @param no_detection 不检测连通性
	 * @return 
	 */
	public Set<InetSocketAddress> addPublicNodes(Set<InetSocketAddress> addresses, boolean no_detection) {
			logInfo("检测节点列表..");
			return public_nodes_list.addAll(addresses, new Predicate<InetSocketAddress>() {
				@Override
				public boolean test(InetSocketAddress socketAddr) {
					// 对节点列表进行探测
					InetAddress addr = socketAddr.getAddress();
					return isOwnedAddress(addr) || !isInternetAddress(addr) || addr == getServerAddress() || !no_detection && !detect(addr, socketAddr.getPort());
				}
			});

	}
	
	
	/**
	 * 添加并检测并共享公共节点列表
	 * @param addresses
	 * @param from 来源,可为null
	 * @return 
	 */
	public void addAndSharePublicNodes(Set<InetSocketAddress> addresses,NodeSocket from, boolean dont_detect) {
		logInfo("添加节点..", from);
		Set<InetSocketAddress> newPublicAddresses = addPublicNodes(addresses, dont_detect);
		if (newPublicAddresses.size() > 0) {
			logInfo("转发节点..", from);
			if (onNodesFound != null) {
				onNodesFound.trigger(newPublicAddresses);
			}
			requestNeighbors(new NewNodesShare(newPublicAddresses), from, 0);
		}
	}

	/**
	 * 检查一个ip地址是否是互联网可见
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
	 * 检查是否本机地址
	 * 
	 * @param addr
	 * @return
	 */
	private static boolean isOwnedAddress(InetAddress addr) {
		return local_addresses.contains(addr);
	}

	/**
	 * 广播消息
	 * 
	 * @param type
	 *            消息类型
	 * @param buffer
	 *            消息内容
	 * @return 成功发送到多少节点
	 */
	public int broadcast(Message message) {
		//worker_pool.execute(new MessageCallback(message, null, true));//消息给自己发送一份
		return flood(packMessage(message, true, new Random().nextInt(Integer.MAX_VALUE),  netID), null);
	}

	/**
	 * 随机向邻近节点发送请求
	 * @param message 消息
	 * @param exclude 剔除的邻居
	 * @param limit 请求邻居数量
	 * @return
	 */
	public int requestNeighbors(Message message, NodeSocket exclude,int limit) {
		return flood(packMessage(message, false, 0,  netID), exclude, limit);
	}
	
	/**
	 * 消息泛洪
	 * @param _msg
	 * @param exclude
	 * @param limit 限制发送给节点数量
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
					if (sock.isClient) {
						last_active_time.replace(sock, getTimestamp());
					}
					if (++nSuccess > limit && limit > 0) {
						break;
					}
				} catch (IOException e) {
					delConnected(sock);
				}
			}
		}
		logInfo("泛洪消息给" + nSuccess + "个节点");

		return nSuccess;
	}
	
	/**
	 * 不限制数量进行泛洪
	 * @param _msg
	 * @param exclude
	 * @return
	 */
	int flood(byte[] _msg, NodeSocket exclude) {
		return flood(_msg, exclude, 0);
	}
	
	

	// <<<客户端模式

	// <<<实现两种运行模式,Server、Client

	// >>>公共接口

	/**
	 * 作为客户端主动连接
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

		// 开始心跳
		if (last_active_time == null) {
			last_active_time = new ConcurrentHashMap<NodeSocket, Integer>();
			logInfo("开始心跳，" + heart_beat_interval + "秒每次");
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

		new MessageLoopThread(sock).start();// 开启消息循环
		logInfo("主动连接", sock);

	}

	/**
	 * 移除节点
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
			last_active_time.remove(sock);
		} catch (Exception e) {
		}

	}

	public void finalize() {
		shutdown();
	}

	/**
	 * 获取本地服务器地址
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
	 * 获取本地服务器端口
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
	 * 监听随机端口
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

	// <<<公共接口

	/**
	 * 监听指定端口
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

	// >>>服务器模式
	/**
	 * 作为服务器监听端口
	 * 
	 * @param port
	 * @param backlog
	 * @param bindAddr
	 * @throws DuplicateBinding
	 * @throws IOException
	 */
	public void listen(int port, int backlog, InetAddress bindAddr, ListenSuccessEvent onSuccess) throws IOException {
		sock_serv = new ServerSocket(port, backlog, bindAddr);
		// >>>开始监听连接

		logInfo("开始监听,本地地址:" + sock_serv.getInetAddress().getHostAddress() + ":" + sock_serv.getLocalPort());
		
			listen_thread = new Thread() {
			public void run() {
				while (true) {
					try {
						NodeSocket sock = new NodeSocket(sock_serv.accept());
						// >>>启动消息循环线程
						new MessageLoopThread(sock).start();
						// <<<启动消息循环线程
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
			//中断,结束监听
			logInfo("中断监听");
		}
		sock_serv.close();
		// <<<开始监听连接
	}

	/**
	 * 调试日志
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
			logInfo(string + ",远程节点:" + sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
		}
		
	}

	/**
	 * 向一个节点发送消息
	 * 
	 * @param sock
	 * @param message
	 * @param isForward
	 *            是否转发
	 * @return 失败false
	 * @throws MessageTooLong
	 */
	boolean sendTo(NodeSocket sock, Message message) {
		logInfo(message.getClass() + "--->>>", sock);
		try {
			OutputStream rawOut = sock.getOutputStream();
			rawOut.write(packMessage(message, false, 0,  netID));
			rawOut.flush();
			if (sock.isClient) {
				last_active_time.replace(sock, getTimestamp());
			}
		} catch (IOException e) {
			e.printStackTrace();
			delConnected(sock);
			return false;
		}	
		return true;
	
	}

	private byte[] packMessage(Message message, boolean isFlood, int serial, byte[] netID) {
		ByteArrayOutputStream arr_out = new ByteArrayOutputStream();
		DataOutputStream data_arr_out = new DataOutputStream(arr_out);
		ByteArrayOutputStream arr_out_complete = new ByteArrayOutputStream();
		try {
			message.output(data_arr_out);// 打包消息体
			int msg_len = data_arr_out.size();// 取得消息长度
			if (msg_len > 65535) {
				System.err.println("Message type " + message.getCode() + " too long");
				return null;
			}
			int msg_code = message.getCode();// 消息类型
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
			
			
			// 重新组装消息
			data_arr_out = new DataOutputStream(arr_out_complete);
			data_arr_out.write(msg_type | (isFlood ? 0x80:0));
			if (msg_type == 7) {
				if (Arrays.equals(netID, emptyNetID)) {
					logInfo("打包失败,空网络");
					return null;
				}
				data_arr_out.write(netID);
				data_arr_out.writeShort(msg_code);
			}
			data_arr_out.writeShort(msg_len);
			if (isFlood) {
				data_arr_out.writeInt(serial);//32位序列号区分不同消息
			}
			
			arr_out.writeTo(arr_out_complete);
		} catch (IOException e) {

			e.printStackTrace();
			return null;
		}
		return arr_out_complete.toByteArray();
	}


	/**
	 * 关闭服务
	 */
	public void shutdown() {
		logInfo("服务关闭..");
		//结束监听
		if (listen_thread != null && listen_thread.isAlive()) {
			listen_thread.interrupt();
		}
		
		message_loop_group.interrupt();//关闭消息循环
		
		if (heart_beater != null) {
			heart_beater.shutdown();// 停止心跳
		}
		
		worker_pool.shutdown();// 结束工作线程池
		
		// 断开所有节点
		for (NodeSocket sock : connected_nodes) {
			delConnected(sock);
		}

		logInfo("关闭完成");
	}

	/**
	 * 探测节点
	 * 
	 * @param addr
	 * @param port
	 * @return 存活返回true
	 */
	public boolean detect(InetAddress addr, int port) {
		logInfo(addr.getHostAddress() + "开始检测");

		NodeSocket sock = null;
		try {
			Socket _sock = new Socket();
			_sock.connect(new InetSocketAddress(addr, port), connect_timeout);
			sock = new NodeSocket(_sock);
			// 发送协议头
			sock.getOutputStream().write(HANDSHAKE_FLAG);
			// 发送探测消息
			sendTo(sock, new NodeDetection());

			// 获取响应
			DataInputStream in = new DataInputStream(sock.getInputStream());

			// 验证协议头
			byte[] buffer = new byte[HANDSHAKE_FLAG.length];
			in.readFully(buffer);
			if (!MessageDigest.isEqual(buffer, HANDSHAKE_FLAG)) {
				return false;
			}

			int msg_type = in.readUnsignedByte();
			
			if (msg_type != 1) {
				return false;
			}
			logInfo(addr.getHostAddress() + "检测完成");
			return true;
		} catch (Exception e) {
			if (debug) {
				e.printStackTrace();
			}
			logInfo(addr.getHostAddress() + "检测失败");
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
