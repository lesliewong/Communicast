package collection.hashgold;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


/**
 * ��¡������,����ת��ʱ�����ظ���Ϣ
 * @author huangkaixuan
 *
 */
public class BloomFilter {
	public static final int MAP_CHECKPOINT_NUM = 6;//���ö��ٸ�����
	
	private final int bit_map_size;//��������С,bits
	private final int check_block_len;
	private byte[] map;
	
	/**
	 * ָ���������,λ��ʵ����������
	 * @param period
	 * @param map_size ��������С,�ֽ�
	 */
	public BloomFilter(int period, int map_size) {
		bit_map_size = map_size * 8;
		check_block_len = (int)Math.ceil( (Math.log(bit_map_size) / Math.log(2)));
		clear();
	}
	
	
	/**
	 * �������
	 * @param buffer
	 * @return �����Ѵ��ڷ���false
	 */
	public boolean add(byte[] buffer) {
		try {
			buffer = MessageDigest.getInstance("MD5").digest(buffer);
			int startByte;
			int startBit;
			int bitMapPointer;
			int consumed;
			for(int i = 0; i< MAP_CHECKPOINT_NUM; i++) {
				startBit = check_block_len * i ;
				startByte = startBit/8;
				startBit %= 8;
				//(buffer[startByte] >> (8 - startBit - )) & ((byte)0x8f >> (Math.min(8, check_block_len) -1 ));
			
			}
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return true;
		
	}
	
	public boolean exists(byte[] digest) {
		return false;
	}
	
	/**
	 * ���λ��
	 */
	private void clear() {
		map = new byte[bit_map_size];
	}
}
