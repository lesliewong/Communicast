package msg.hashgold;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.hashgold.Responser;

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
	public int getType();
	
	
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
	public void output(OutputStream out) throws IOException;
	
	
	/**
	 * ����������Ϣ
	 * @param in
	 */
	public void input(InputStream in, int len) throws IOException;
}
