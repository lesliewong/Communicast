package collection.hashgold;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ��¡������,����ת��ʱ�����ظ���Ϣ
 * 
 * @author huangkaixuan
 *
 */
public class BloomFilter {
	public static final int MAP_CHECKPOINT_NUM = 6;// ���ö��ٸ�����

	private final int bit_map_size;// ��������С,bits
	private final double load_factor;
	private final int check_block_len;
	private Byte[] map;
	private final AtomicInteger map_length;
	private final ReentrantReadWriteLock lock;
	private final Condition condition_full;
	private final Thread clear_thread;//�����߳�
	private final int mask = ~0 << 7;

	/**
	 * ָ���������,λ��ʵ����������
	 * 
	 * @param period
	 * @param map_sizeW
	 *            ��������С,�ֽ�
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
		load_factor = loadFactor;
		map_length = new AtomicInteger();
		lock = new ReentrantReadWriteLock();
		condition_full = lock.writeLock().newCondition();
		clear_thread = new Thread() {
			public void run() {
				clear();
			}
		};
		map = new Byte[bit_map_size/8];
		clear_thread.setDaemon(true);
		clear_thread.start();
	}

	/**
	 * �������
	 * 
	 * @param buffer
	 * @return �����Ѵ��ڷ���false
	 */
	public boolean add(byte[] buffer) {
		try {
			buffer = MessageDigest.getInstance("SHA-512").digest(buffer);
			int current_byte = 0;
			int bit_offset = 0;
			int consumed = 0;
			int to_consume_bits;
			int map_position = 0;

			
			int check_byte;
			int check_bit;
			boolean exists = true;
			
			lock.readLock().lock();
			do {
				to_consume_bits = Math.min(8 - bit_offset, check_block_len - consumed);
				map_position |= (buffer[current_byte] << bit_offset & mask >> to_consume_bits >>> (8-to_consume_bits) & 0xff) << consumed;
				consumed += to_consume_bits;
				bit_offset += to_consume_bits;
				if (bit_offset > 7) {
					bit_offset %= 8;
					current_byte++;
				}
				
				if (consumed % check_block_len == 0) {
					//����
					long pointer = Integer.toUnsignedLong(map_position);
					check_byte = (int) (pointer/8);
					check_bit = (int) (pointer % 8);
					if ((map[check_byte] << check_bit & 0x80) != 0x80) {
						//��Ϊ1
						exists = false;
						synchronized(map[check_byte]) {
							map[check_byte] =  (byte) (map[check_byte] | 0x80 >> check_bit);
						}
					}
				
					map_position = 0;
				}
				
				
				
			} while (consumed < check_block_len * MAP_CHECKPOINT_NUM);
			
			if (!exists) {
				//����+1
				if (map_length.incrementAndGet() == bit_map_size * load_factor) {
					//���
					lock.writeLock().lock();
					condition_full.signalAll();
					lock.writeLock().unlock();
				} 
				
			} 
			lock.readLock().unlock();
			
			return !exists;
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return true;

	}

	/**
	 * ���λ��
	 */
	private void clear() {
		if (!lock.writeLock().tryLock()) {
			return;
		}
		do {
			condition_full.awaitUninterruptibly();
			map_length.set(0);
			map = new Byte[bit_map_size/8];
		} while (true);
	}
}

@SuppressWarnings("serial")
class InvalidFilterSize extends Exception {

	public InvalidFilterSize(String string) {
		super(string);
	}
}
