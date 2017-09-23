package msg.hashgold;

import java.util.HashMap;

import exception.hashgold.UnrecognizedMessage;

public class Registry {
	private static final HashMap<Integer, Class<? extends Message>> message_map;
	
	static {
		message_map = new HashMap<Integer, Class<? extends Message>>();
	}
	
	/**
	 * ע��һ����Ϣ
	 * @param message
	 */
	public static void registerMessage(Message message) {
		message_map.put(message.getType(), message.getClass());
	}
	
	
	/**
	 * ��ȡָ����Ϣ����ʵ��
	 * @param type
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws UnrecognizedMessage 
	 */
	public static Message newMessageInstance(int type) throws InstantiationException, IllegalAccessException, UnrecognizedMessage {
		Class<? extends Message> class_name = message_map.get(type);
		
		if (class_name == null) {
			throw new UnrecognizedMessage(type);
		}
		return class_name.newInstance();
	}
}