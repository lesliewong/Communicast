package collection.hashgold;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 布隆过滤器,用于转发时过滤重复消息
 * 
 * @author huangkaixuan
 *
 */
public class BloomFilter {
	public static final int MAP_CHECKPOINT_NUM = 6;// 设置多少个检查点

	private final int bit_map_size;// 过滤器大小,bits
	private final int critical_size; //临界大小
	private final int check_block_len;
	private final Byte[] map;
	private final AtomicInteger map_length;
	private final ReentrantReadWriteLock lock;
	private final Condition condition_full;
	private final Thread clear_thread;//清理线程
	private final byte[] collision_mask;//64位冲突掩码,避免一个消息在全网同时发生碰撞而广播失败
	private final int mask = ~0 << 8;

	/**
	 * 指定清空周期,位数实例化过滤器
	 * 
	 * @param period
	 * @param map_sizeW
	 *            过滤器大小,字节
	 * @throws InvalidFilterSize
	 */
	public BloomFilter(int map_size, double loadFactor) throws InvalidFilterSize {
		bit_map_size = map_size * 8;
		double block_len = Math.log(bit_map_size) / Math.log(2);
		check_block_len = (int) Math.ceil(block_len);
		if (check_block_len > block_len) {
			throw new InvalidFilterSize("Filter size has to be a power of two");
		}
		
		int max_size = (int) Math.pow(2,512/MAP_CHECKPOINT_NUM);
		if (bit_map_size > max_size) {
			throw new InvalidFilterSize("Filter too big,maximum:"+max_size/8+"B");
		}
		
		//System.out.println("check block len " + check_block_len);
		critical_size = (int) (loadFactor * bit_map_size);
		collision_mask = new byte[8];
		new Random().nextBytes(collision_mask);
		map_length = new AtomicInteger();
		lock = new ReentrantReadWriteLock();
		condition_full = lock.writeLock().newCondition();
		clear_thread = new Thread() {
			public void run() {
				if (!lock.writeLock().tryLock()) {
					return;
				}
				do {
					condition_full.awaitUninterruptibly();
					clear();
				} while (true);
			}
		};
		map = new Byte[bit_map_size/8];
		clear();
		//System.out.println("过滤器大小:" + map.length + "B");
		clear_thread.setDaemon(true);
		clear_thread.start();
	}
	
	/**
	 * 用冲突掩码混合数据
	 * @param buffer
	 */
	private void mixWithMaskBytes(byte[] buffer) {
		for(int i = 0; i < collision_mask.length; i++) {
			buffer[i] &= collision_mask[i];
		}
	}

	/**
	 * 添加数据
	 * 
	 * @param buffer
	 * @return 数据已存在返回false
	 */
	public boolean add(byte[] buffer) {
		try {
			buffer = MessageDigest.getInstance("SHA-512").digest(buffer);
			mixWithMaskBytes(buffer);
			int current_byte = 0;
			int bit_offset = 0;
			int consumed = 0;
			int to_consume_bits;
			int map_position = 0;

			
			int check_byte;
			int check_bit;
			int current_consume_index = 0;
			boolean exists = true;
			boolean full = false;
			
			lock.readLock().lock();
			do {
				to_consume_bits = Math.min(8 - bit_offset, check_block_len - current_consume_index);
				//System.out.println("Current byte index " + current_byte + ",byte value " + Integer.toBinaryString(buffer[current_byte] & 0xff) +",bitoffset:"+bit_offset);
				int mid = (buffer[current_byte] << bit_offset & mask >> to_consume_bits & 0xff) >> (8 - to_consume_bits) << current_consume_index ;
				map_position |= mid;
				consumed += to_consume_bits;
				//System.out.println("Consumed bits:" + to_consume_bits + ",consumed:" + consumed+",mid result:" + Integer.toBinaryString(mid) + ",current position value " + Integer.toBinaryString(map_position));
				
				bit_offset += to_consume_bits;
				if (bit_offset > 7) {
					bit_offset %= 8;
					current_byte++;
				}
				current_consume_index = consumed%check_block_len;
				
				if (current_consume_index == 0) {
					//检查点
					long pointer = Integer.toUnsignedLong(map_position);
					
					check_byte = (int) (pointer/8);
					check_bit = (int) (pointer % 8);
					//System.out.println("指针位置:" + pointer + "/" + bit_map_size + ",字节:" + check_byte + ",比特:" + check_bit);
					if ((map[check_byte] << check_bit & 0x80) != 0x80) {
						//置为1
						exists = false;
						synchronized(map[check_byte]) {
							map[check_byte] =  (byte) (map[check_byte] | 0x80 >> check_bit);
						}
						full = full || map_length.incrementAndGet() == critical_size; //到达临界值,需要清空
					}
				
					map_position = 0;
				}
				
				
				
			} while (consumed < check_block_len * MAP_CHECKPOINT_NUM);
			
			lock.readLock().unlock();
				
			if (full) {
				//清空
				lock.writeLock().lock();
				condition_full.signalAll();
				lock.writeLock().unlock();
			} 
	
			
			return !exists;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return true;

	}

	/**
	 * 清空位表
	 */
	private void clear() {
		//System.out.println("清空");
		map_length.set(0);
		for(int i = 0; i < map.length; i ++) {
			map[i] = 0;
		}
	
	}
}

@SuppressWarnings("serial")
class InvalidFilterSize extends Exception {

	public InvalidFilterSize(String string) {
		super(string);
	}
}
