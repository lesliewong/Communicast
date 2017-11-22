package util.hashgold;

import java.security.MessageDigest;


/**
 * 工作量证明
 * @author huangkaixuan
 *
 */
public class WorkProof {
	private final int difficulity_required;
	private final String algorithm;
	private byte[] nonce;
	private static final int MIN_NONCE_SIZE;//nonce最小尺寸
	private final boolean suffix;
	private MessageDigest digest;
	private WorkResult result;
	private boolean quit;
	private final byte[] source;
	
	static {
		MIN_NONCE_SIZE = 3;//nonce最小3个字节
	}
	
	/**
	 * 工作结果
	 * @author huangkaixuan
	 *
	 */
	public class WorkResult {
		public final int difficulity;
		public final byte[] nonce;
		public final byte[] source;
		
		private WorkResult(int difficulity) {
			nonce = WorkProof.this.nonce;
			this.difficulity = difficulity;
			this.source = WorkProof.this.source;
		}
	}
	
	/**
	 * 结束计算
	 */
	public void quit() {
		quit = true;
	}
	
	
	/**
	 * 实例化工作量任务
	 * @param data 计算工作量的任务
	 * @param difficulity 难度,输出有多少个0开头
	 * @param hash_algorithm 哈希算法
	 * @param suffix nonce是否附加在数据结尾,false置于开头
	 */
	public WorkProof(byte[] data, int difficulity, String hash_algorithm, boolean suffix)  {
		algorithm = hash_algorithm;
		source = data;
		this.suffix = suffix;
		difficulity_required = difficulity;
	}
	
	/**
	 * 开始计算工作量
	 * @return 成功返回true,被中断返回false
	 * @throws Exception 
	 */
	public boolean startWork() throws Exception {
		//工作已经完成
		if (quit) {
			return false;
		}
		
		digest = MessageDigest.getInstance(algorithm);
		if (difficulity_required > digest.getDigestLength() * 8) {
			throw new Exception("Difficulity required too long");
		}
		byte[] buffer = new byte[source.length + MIN_NONCE_SIZE];
		nonce = new byte[MIN_NONCE_SIZE];
		System.arraycopy(source, 0, buffer, suffix ? 0 : MIN_NONCE_SIZE, source.length);		
		int difficulity;
		do {
			if (source.length + nonce.length > buffer.length) {
				byte[] temp = buffer;
				buffer = new byte[source.length + nonce.length];
				System.arraycopy(temp, suffix ? 0:nonce.length - 1, buffer, suffix ? 0:nonce.length, source.length);		
			}

			System.arraycopy(nonce, 0, buffer, suffix ? source.length: 0, nonce.length);
			difficulity = getDifficulity(digest.digest(buffer));
			if (difficulity < difficulity_required) {
				carry(0);
			} else {
				break;
			}
		} while ( !quit);
		result = new WorkResult(difficulity);
		return result.difficulity >= difficulity_required;
	}
	
	/**
	 * 取得工作量结果
	 * @return
	 */
	public WorkResult getResult() {
		return result;
	}
	
	/**
	 * nonce进位
	 * @param i
	 * @return void
	 */
	private void carry(int i) {
		if (i >= nonce.length) {
			//溢出,增加Nonce长度
			byte[] temp = nonce;
			nonce = new byte[nonce.length + 1 ];
			System.arraycopy(temp, 0, nonce, 0, temp.length);
		}
		
		if (++nonce[i] == 0) {
			carry(i+1);
		}
	}
	
	/**
	 * 取得哈希的难度(前缀零的数量)
	 * @param digest
	 * @return
	 */
	public static int getDifficulity(byte[] digest) {
		int difficulity = 0;
		for(int i = 0; i< digest.length; i++) {
			if (digest[i] == 0) {
				difficulity += 8;
			} else {
				//last byte
				for(int j = 0; j<8; j++) {
					if ((digest[i] & (0x80 >> j)) == 0) {
						difficulity ++;
					}  else {
						break;
					}
				}
				break;
			}
		}
		return difficulity;
	}
}
