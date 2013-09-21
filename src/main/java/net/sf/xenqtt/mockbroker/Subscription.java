package net.sf.xenqtt.mockbroker;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import net.sf.xenqtt.message.ConnectMessage;
import net.sf.xenqtt.message.PubAckMessage;
import net.sf.xenqtt.message.PubMessage;
import net.sf.xenqtt.message.QoS;

/**
 * A {@link Client client's} subscription to a {@link Topic topic}
 */
final class Subscription {

	private final String clientId;
	QoS subscribedQos;
	private final Queue<PubMessage> messageQueue;

	/**
	 * Creates a subscription to a topic
	 */
	Subscription(String clientId, QoS subscribedQos) {
		this.clientId = clientId;
		this.subscribedQos = subscribedQos;
		this.messageQueue = new LinkedList<PubMessage>();
	}

	/**
	 * Called when a {@link ConnectMessage} is received
	 */
	void connected(Client client) {

		for (PubMessage pub : messageQueue) {
			send(client, pub);
		}
	}

	/**
	 * Called when a {@link PubAckMessage} is received
	 * 
	 * @return true if this specified message is in the subscription queue for the specified client
	 */
	boolean pubAcked(int messageId) {
		Iterator<PubMessage> iter = messageQueue.iterator();
		while (iter.hasNext()) {
			PubMessage pub = iter.next();
			if (pub.getMessageId() == messageId) {
				iter.remove();
				return true;
			}
		}

		return false;
	}

	/**
	 * Called when a {@link PubMessage} is received
	 */
	void publish(PubMessage message, Map<String, Client> clientById) {

		if (message.getQoSLevel() > 0 && subscribedQos.value() > 0) {
			messageQueue.add(message);
		}

		Client client = clientById.get(clientId);
		if (client != null) {
			send(client, message);
		}
	}

	private void send(Client client, PubMessage message) {

		if (subscribedQos.value() < message.getQoSLevel()) {
			if (subscribedQos.value() > 0) {
				message.setMessageId(client.getNextMessageId());
			}
			message = new PubMessage(subscribedQos, message.isRetain(), message.getTopicName(), message.getMessageId(), message.getPayload());
		} else if (message.getQoSLevel() > 0) {
			message.setMessageId(client.getNextMessageId());
		}

		client.send(message);
	}
}
