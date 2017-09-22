package msg.hashgold;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import net.hashgold.Responser;

/**
 * 消息接口
 * @author huangkaixuan
 *
 */
public interface Message {
	/**
	 * 返回一个整型的消息类型
	 * @return
	 */
	public int getType();
	
	
	/**
	 * 消息接收事件
	 * @param respon TODO
	 */
	public void onReceive(Responser respon);
	
	
	/**
	 * 将消息输出到流
	 * @param out
	 * @throws IOException 
	 */
	public void output(OutputStream out) throws IOException;
	
	
	/**
	 * 从流读入消息
	 * @param in
	 */
	public void input(InputStream in, int len) throws IOException;
}
