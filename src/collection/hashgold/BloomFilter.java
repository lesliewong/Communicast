package collection.hashgold;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * ��¡������,����ת��ʱ�����ظ���Ϣ
 * @author huangkaixuan
 *
 */
public class BloomFilter {
	public static final int HASH_TRANSFORM_ROUNDS = 6;//���ж����ֹ�ϣת��
	
	private final int bit_map_size;//��������С,�ֽ�
	private byte[] map;
	
	/**
	 * ָ���������,λ��ʵ����������
	 * @param period
	 * @param max_capacity
	 */
	public BloomFilter(int period, int map_size) {
		bit_map_size = map_size;
		clear();
	}
	
	
	/**
	 * �������
	 * @param buffer
	 * @return �����Ѵ��ڷ���false
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
	 * ���λ��
	 */
	private void clear() {
		map = new byte[bit_map_size];
	}
}
