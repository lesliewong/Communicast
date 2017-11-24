package net.hashgold;

import msg.hashgold.Message;

public interface PostBroadcastEvent {
	public void trigger(Message message, int sent_to, Node self);
}
