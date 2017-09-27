package collection.hashgold;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * 布隆过滤器,用于转发时过滤重复消息
 * @author huangkaixuan
 *
 */
public class BloomFilter {
	public static final int HASH_TRANSFORM_ROUNDS = 6;//进行多少轮哈希转换
	
	private final int bit_map_size;//过滤器大小,字节
	private byte[] map;
	
	/**
	 * 指定清空周期,位数实例化过滤器
	 * @param period
	 * @param max_capacity
	 */
	public BloomFilter(int period, int map_size) {
		bit_map_size = map_size;
		clear();
	}
	
	
	/**
	 * 添加数据
	 * @param buffer
	 * @return 数据已存在返回false
	 */
	public boolean add(byte[] buffer) {
		byte[] to_digest = new byte[buffer.length + 1];
		System.arraycopy(buffer, 0, to_digest, 0, buffer.length);
		buffer = null;
		
		for(int i = 1; i<= HASH_TRANSFORM_ROUNDS; i++){
//			to_digest[to_digest.length - 1] = (byte)i;
//			bit_map_size * 8
		}
		
		try {
			MessageDigest.getInstance("MD5").digest(buffer);
			
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return true;
		
	}
	
	/**
	 * 清空位表
	 */
	private void clear() {
		map = new byte[bit_map_size];
	}
}
