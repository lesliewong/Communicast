package collection.hashgold;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.atomic.AtomicInteger;
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

	/**
	 * ָ���������,λ��ʵ����������
	 * 
	 * @param period
	 * @param map_size
	 *            ��������С,�ֽ�
	 * @throws InvalidFilterSize
	 */
	public BloomFilter(int map_size, double loadFactor) throws InvalidFilterSize {
		bit_map_size = map_size * 8;
		double block_len = Math.log(bit_map_size) / Math.log(2);
		check_block_len = (int) Math.ceil(block_len);
		if (check_block_len > block_len) {
			throw new InvalidFilterSize(block_len);
		}
		load_factor = loadFactor;
		map_length = new AtomicInteger();
		lock = new ReentrantReadWriteLock();
		clear();
	}

	/**
	 * �������
	 * 
	 * @param buffer
	 * @return �����Ѵ��ڷ���false
	 */
	public boolean add(byte[] buffer) {
		try {
			buffer = MessageDigest.getInstance("MD5").digest(buffer);
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
				map_position |= (buffer[current_byte] << bit_offset & 0x80 >> to_consume_bits & 0xff) << consumed;
				consumed += to_consume_bits;
				bit_offset += to_consume_bits;
				if (bit_offset > 7) {
					bit_offset %= 8;
					current_byte++;
				}
				
				if (consumed % check_block_len == 0) {
					//����
					check_byte = map_position/8;
					check_bit = map_position % 8;
					if ((map[check_byte] << check_bit & 0x80) != 0x80) {
						//��Ϊ1
						exists = false;
						synchronized(map[check_byte]) {
							map[check_byte] =  (byte) (map[check_byte] | 0x80 >>> check_bit);
						}
					}
				
					map_position = 0;
				}
				
				
				
			} while (consumed < check_block_len * MAP_CHECKPOINT_NUM);
			
			if (!exists) {
				//����+1
				int current_len = map_length.incrementAndGet();
				lock.readLock().unlock();
				if (current_len == bit_map_size * load_factor) {
					//���
					clear();
				} 
				
			} else {
				lock.readLock().unlock();
			}
			
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
		lock.writeLock().lock();
		map_length.set(0);
		map = new Byte[bit_map_size/8];
		lock.writeLock().unlock();
	}
}

class InvalidFilterSize extends Exception {

	public InvalidFilterSize(double block_len) {
		super(""+block_len);
	}
}
