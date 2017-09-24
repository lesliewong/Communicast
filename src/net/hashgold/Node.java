package net.hashgold;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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

import collection.hashgold.LimitedRandomSet;
import exception.hashgold.ConnectionFull;
import exception.hashgold.DuplicateMessageNumber;
import exception.hashgold.UnrecognizedMessage;
import msg.hashgold.ConnectionRefuse;
import msg.hashgold.HeartBeat;
import msg.hashgold.Message;
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
		private Message _msg;
		private NodeSocket _sock;

		MessageCallback(Message message, NodeSocket sock) {
			_msg = message;
			_sock = sock;
		}

		@Override
		public void run() {
			logInfo("收到消息" + _msg.getClass(), _sock);
			_msg.onReceive(new Responser(Node.this, _sock));
			
			//收到消息后发现连接未加入或被移除则关闭socket
			if (!connected_nodes.contains(_sock)) {
				try {
					_sock.close();
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
						logInfo("接收新连接", _sock);

						InputStream in = _sock.getInputStream();

						// 发送协议头
						_sock.getOutputStream().write(HANDSHAKE_FLAG);

						// 验证协议头
						byte[] buffer = new byte[HANDSHAKE_FLAG.length];
						in.read(buffer);
						// logInfo("协议头:"+new String(buffer));
						if (!MessageDigest.isEqual(buffer, HANDSHAKE_FLAG)) {
							_sock.close();
							logInfo("无效协议头", _sock);
							return;
						}
						buffer = null;

						// 客户端发起心跳激活会话
						if (_sock.isClient) {
							sendTo(_sock, new HeartBeat());
						}

						// >>>循环读取消息

						/**
						 * 消息格式,传输的都是无符号数 =========================== ||1B
						 * 消息类型|2B 消息长度|NB 正文|| ===========================
						 */

						int msg_type;// 消息类型
						int msg_len;// 消息长度
						boolean node_added = false;

						do {
							msg_type = in.read() & 0xff;
							msg_len = ((in.read() << 8 )& 0xffff) + (in.read() & 0xff);
							try {
								Message msg;

								msg = Registry.newMessageInstance(msg_type);

								msg.input(in, msg_len);
								logInfo("消息长度" + msg_len, _sock);

								//收到第一个消息
								if (!node_added) {
									if (_sock.isClient) {
										//客户端
										
										if (! (msg instanceof ConnectionRefuse)) {//服务器未拒绝
											node_added = true;
										}
									} else {
										//服务端
										if (!(msg instanceof NodeDetection)) {
											if (connected_nodes.size() >= max_connections) {
												//检查是否超过服务器最大连接数
													sendTo(_sock, new ConnectionRefuse("Connections are full"));
													logInfo("超过最大连接数" + max_connections, _sock);
											} else {
												//接受新连接
												node_added = true;
											}	
										}
										
									}
									
									if (node_added) {
										addConnected(_sock);
										logInfo("加入新节点", _sock);
									}
								}
								
								//只有接受的连接或者探测和拒绝消息被处理
								if (node_added || msg instanceof NodeDetection || msg instanceof ConnectionRefuse) {
									worker_pool.execute(new MessageCallback(msg, _sock));
								}
								
								//更新服务器响应时间
								if (node_added && _sock.isClient) {
									last_active_time.put(_sock, getTimestamp());
								}
								
								//未加入连接池则退出消息循环
								if (!node_added) {
									return;
								}
							} catch (InstantiationException | IllegalAccessException e) {
								e.printStackTrace();
								break;
							} catch (UnrecognizedMessage e) {
								// 无法识别的消息,断开连接
								break;
							}

						} while (true);
						// <<<循环读取消息

					} catch (IOException e) {
						// 消息读取出错
					}
					logInfo("节点被移除", _sock);
					delConnected(_sock);

				}
			});
			this.setDaemon(true);
		}

	}

	public static final byte[] HANDSHAKE_FLAG;// 协议握手标识

	public static final int heart_beat_interval = 15;// 心跳间隔秒,超过一个心跳间隔未收到对方消息则主动发出一个心跳,超过3个心跳间隔时间无响应将断开连接

	public static int public_nodes_list_size = 500; // 保存公共节点数量限制

	public int max_connections = 50;// 最大连接数量;

	public boolean debug = false;// 调试日志

	static {
		HANDSHAKE_FLAG = "HASHGOLD".getBytes();

		// 注册消息类型
		try {
			Registry.registerMessage(new HeartBeat());// 心跳0
			Registry.registerMessage(new NodeDetection());// 探测节点1
			Registry.registerMessage(new NodesExchange());// 节点列表交换2
			Registry.registerMessage(new ConnectionRefuse());// 拒绝连接3
		} catch (DuplicateMessageNumber e) {
			e.printStackTrace();
		}

	}

	private static int getTimestamp() {
		return (int) (System.currentTimeMillis() / 1000);
	}

	// >>>属性初始化
	private ServerSocket sock_serv;// 服务器类单例

	private final CopyOnWriteArrayList<NodeSocket> connected_nodes;// 连接节点

	private Thread listen_thread;// 监听线程

	private final ThreadGroup message_loop_group;// 消息循环线程组

	private ConcurrentHashMap<NodeSocket, Integer> last_active_time;// 心跳状态,socket
																	// =>
																	// 最后接收消息时间(秒)

	private ScheduledExecutorService heart_beater;// 心跳起搏器

	private final ExecutorService worker_pool;// 消息处理线程池

	public NodeConnectedEvent onConnect; // 连接订阅者

	public NodeDisconnectEvent onDisconnecct; // 断开订阅者

	private LimitedRandomSet<InetSocketAddress> public_nodes_list; // 公共节点列表

	private static final ArrayList<InetAddress> local_addresses; // 本地IP地址

	static {
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
	public Node() {
		connected_nodes = new CopyOnWriteArrayList<NodeSocket>();
		message_loop_group = new ThreadGroup("MESSAGE_LOOP");

		// 初始化工作线程池
		worker_pool = Executors.newCachedThreadPool();

		// 公共节点列表
		try {
			public_nodes_list = new LimitedRandomSet<InetSocketAddress>(public_nodes_list_size);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 获取部分公共节点
	 * @param n
	 * @return
	 */
	public Set<InetSocketAddress> getPublicNodes(int n) {
		try {
			return public_nodes_list.pick(n);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	
	/**
	 * 获取全部公共节点
	 * @return
	 */
	public Set<InetSocketAddress> getPublicNodes() {
		return public_nodes_list;
	}

	/**
	 * 添加连接节点
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

	// <<<服务器模式

	// >>>客户端模式

	/**
	 * 添加公共节点
	 * 
	 * @param addresses
	 */
	public void addPublicNodes(Set<InetSocketAddress> addresses) {
		public_nodes_list.addAll(addresses, new Predicate<InetSocketAddress>() {
			@Override
			public boolean test(InetSocketAddress socketAddr) {
				// 对节点列表进行探测
				InetAddress addr = socketAddr.getAddress();
				return !isOwnedAddress(addr) && isInternetAddress(addr)
						&& detect(addr, socketAddr.getPort());
			}
		});
	}

	/**
	 * 检查一个ip地址是否是互联网可见
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
		int nSuccess = 0;
		for (NodeSocket sock : connected_nodes) {
			if (sendTo(sock, message)) {
				nSuccess++;
			}
		}
		return nSuccess;
	}

	/**
	 * 转发来自source的消息
	 * 
	 * @param message
	 * @param source
	 * @return
	 */
	int forward(Message message, NodeSocket source) {
		int nSuccess = 0;
		for (NodeSocket sock : connected_nodes) {
			if (!sock.equals(source) && sendTo(sock, message)) {
				nSuccess++;
			}
		}
		return nSuccess;
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
	 */
	synchronized public void connect(InetAddress dest, int port) throws IOException, ConnectionFull {
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
		
		NodeSocket sock = new NodeSocket(new Socket(dest, port), true);

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
	 * 获取本地服务器地址
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
	 * 获取本地服务器端口
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
	 * 监听随机端口
	 * 
	 * @throws UnknownHostException
	 * @throws DuplicateBinding
	 * @throws IOException
	 */
	public void listen() throws UnknownHostException, IOException {
		listen(0, 50, InetAddress.getByName("0.0.0.0"));
	}

	/**
	 * 等待服务器结束
	 */
	public void waitForServer() {
		if (listen_thread != null) {
			try {
				listen_thread.join();
			} catch (InterruptedException e) {
				// 主进程退出
				shutdown();
			}
		}
	}

	// <<<公共接口

	/**
	 * 监听指定端口
	 * 
	 * @param port
	 * @throws IOException
	 * @throws DuplicateBinding
	 * @throws UnknownHostException
	 */
	public void listen(int port) throws UnknownHostException, IOException {
		listen(port, 50, InetAddress.getByName("0.0.0.0"));
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
	public void listen(int port, int backlog, InetAddress bindAddr) throws IOException {
		sock_serv = new ServerSocket(port, backlog, bindAddr);
		// >>>开启新线程监听连接
		listen_thread = new Thread() {
			public void run() {
				logInfo("开始监听,本地地址:" + sock_serv.getInetAddress().getHostAddress() + ":" + sock_serv.getLocalPort());
				while (true) {

					if (this.isInterrupted()) {
						// 服务器关闭
						try {
							sock_serv.close();
						} catch (IOException e) {
						}
						logInfo("监听关闭");
						return;
					}
					
					try {
						NodeSocket sock = new NodeSocket(sock_serv.accept());
						// >>>启动消息循环线程
						new MessageLoopThread(sock).start();
						// <<<启动消息循环线程
					} catch (IOException e) {
						e.printStackTrace();
						break;
					}
				}

				// 服务器异常结束关闭节点
				shutdown();
			}
		};
		listen_thread.setDaemon(true);
		listen_thread.start();
		// <<<开启新线程监听连接
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
		logInfo(string + ",远程节点:" + sock.getInetAddress().getHostAddress() + ":" + sock.getPort());
	}

	/**
	 * 向一个节点发送消息
	 * 
	 * @param sock
	 * @param message
	 * @return 失败false
	 * @throws MessageTooLong
	 */
	boolean sendTo(NodeSocket sock, Message message) {
		logInfo("发送消息" + message.getClass(), sock);
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

			rawOut.write(message.getType());// 消息类型
			logInfo("消息长度" + msg_len);
			rawOut.write(msg_len >> 8);// 消息长度
			rawOut.write(msg_len);
			out.writeTo(rawOut);// 消息体
			rawOut.flush();
		} catch (IOException e) {
			delConnected(sock);
			return false;
		}
		return true;
	}

	/**
	 * 关闭服务
	 */
	public void shutdown() {
		logInfo("服务关闭..");

		if (listen_thread != null) {
			try {
				listen_thread.interrupt();// 结束监听线程
				sock_serv.close();
			} catch (IOException e) {
			}
		}

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

		try (NodeSocket sock = new NodeSocket(new Socket(addr, port))) {
			// 发送协议头
			sock.getOutputStream().write(HANDSHAKE_FLAG);
			// 发送探测消息
			sendTo(sock, new NodeDetection());

			// 获取响应
			InputStream in = sock.getInputStream();

			// 验证协议头
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
