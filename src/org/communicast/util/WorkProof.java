package org.communicast.util;

import java.security.MessageDigest;


/**
 * ������֤��
 * @author huangkaixuan
 *
 */
public class WorkProof {
	private final int difficulity_required;
	private final String algorithm;
	private byte[] nonce;
	private static final int MIN_NONCE_SIZE;//nonce��С�ߴ�
	private final boolean suffix;
	private MessageDigest digest;
	private WorkResult result;
	private boolean quit;
	private final byte[] source;
	
	static {
		MIN_NONCE_SIZE = 3;//nonce��С3���ֽ�
	}
	
	/**
	 * �������
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
	 * ��������
	 */
	public void quit() {
		quit = true;
	}
	
	
	/**
	 * ʵ��������������
	 * @param data ���㹤����������
	 * @param difficulity �Ѷ�,����ж��ٸ�0��ͷ
	 * @param hash_algorithm ��ϣ�㷨
	 * @param suffix nonce�Ƿ񸽼������ݽ�β,false���ڿ�ͷ
	 */
	public WorkProof(byte[] data, int difficulity, String hash_algorithm, boolean suffix)  {
		algorithm = hash_algorithm;
		source = data;
		this.suffix = suffix;
		difficulity_required = difficulity;
	}
	
	/**
	 * ��ʼ���㹤����
	 * @return �ɹ�����true,���жϷ���false
	 * @throws Exception 
	 */
	public boolean startWork() throws Exception {
		//�����Ѿ����
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
	 * ȡ�ù��������
	 * @return
	 */
	public WorkResult getResult() {
		return result;
	}
	
	/**
	 * nonce��λ
	 * @param i
	 * @return void
	 */
	private void carry(int i) {
		if (i >= nonce.length) {
			//���,����Nonce����
			byte[] temp = nonce;
			nonce = new byte[nonce.length + 1 ];
			System.arraycopy(temp, 0, nonce, 0, temp.length);
		}
		
		if (++nonce[i] == 0) {
			carry(i+1);
		}
	}
	
	/**
	 * ȡ�ù�ϣ���Ѷ�(ǰ׺�������)
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
