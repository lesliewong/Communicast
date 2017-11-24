package org.communicast.msg;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.communicast.net.Responser;

/**
 * ��Ϣ�ӿ�
 * @author huangkaixuan
 *
 */
public interface Message {
	
	/**
	 * ����һ�����͵���Ϣ����
	 * @return
	 */
	public int getCode();
	
	
	/**
	 * ��Ϣ�����¼�
	 * @param respon TODO
	 */
	public void onReceive(Responser respon);
	
	
	/**
	 * ����Ϣ�������
	 * @param out
	 * @throws IOException 
	 */
	public void output(DataOutputStream out) throws IOException;
	
	
	/**
	 * ����������Ϣ
	 * @param in
	 */
	public void input(DataInputStream in, int len) throws IOException;
}
