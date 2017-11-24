package org.communicast.net;

import org.communicast.msg.Message;

public interface PostBroadcastEvent {
	public void trigger(Message message, int sent_to, Node self);
}
