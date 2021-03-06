/**
    Copyright 2013 James McClure

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.xenqtt.message;

import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.List;

/**
 * <p>
 * Sends and receives {@link MqttMessage}s over a channel. This may be client or server side. Since {@link #read(long)} may generate data to send it should
 * always be called before {@link #write(long)}. {@link #houseKeeping(long)} should be called after both {@link #read(long)} and {@link #write(long)} as those
 * methods may change paramaters used to determine what housekeeping is required.
 * </p>
 * <p>
 * If any exception occurs the channel is closed.
 * </p>
 */
public interface MqttChannel extends MqttChannelRef {

	/**
	 * Deregisters this channel. Cancels the underlying {@link SelectionKey}.
	 */
	void deregister();

	/**
	 * Registers this channel with the specified selector. This channel must not already be registered with another selector. The current {@link MessageHandler}
	 * is replaced with the specified one.
	 * 
	 * @return A return value of true does NOT necessarily mean this channel is open but false does mean it is closed (or the connect hasn't finished yet).
	 */
	boolean register(Selector selector, MessageHandler handler);

	/**
	 * Finishes a connection. This should be called when a {@link SelectionKey}s {@link SelectionKey#OP_CONNECT} op is ready.
	 * 
	 * @return True if and only if this channel is now connected.
	 */
	boolean finishConnect();

	/**
	 * Reads data. This will read as many messages as it can and pass them to a {@link MessageHandler}.This should be called when a {@link SelectionKey}s
	 * {@link SelectionKey#OP_READ} op is ready.
	 * 
	 * @param now
	 *            The timestamp to use as the "current" time
	 * 
	 * @return True if the channel is left open. False if it is closed by this method or already closed when this method is called or the connect hasn't
	 *         finished yet.
	 */
	boolean read(long now);

	/**
	 * Pauses reading on the channel. To resume call {@link #resumeRead()}.
	 */
	void pauseRead();

	/**
	 * Resumes reading on this channel.
	 */
	void resumeRead();

	/**
	 * Sends the specified message asynchronously. When a {@link DisconnectMessage} or a {@link ConnAckMessage} where {@link ConnAckMessage#getReturnCode()} is
	 * not {@link ConnectReturnCode#ACCEPTED} is sent the channel is closed automatically.
	 * 
	 * This is the same as calling {@link #send(MqttMessage, BlockingCommand, long)} with a null {@link BlockingCommand} and 0 timestamp.
	 * 
	 * @param message
	 *            The message to send
	 * 
	 * @return A return value of true does NOT necessarily mean this channel is open but false does mean it is closed (or the connect hasn't finished yet).
	 */
	boolean send(MqttMessage message);

	/**
	 * Sends the specified message asynchronously. When a {@link DisconnectMessage} or a {@link ConnAckMessage} where {@link ConnAckMessage#getReturnCode()} is
	 * not {@link ConnectReturnCode#ACCEPTED} is sent the channel is closed automatically.
	 * 
	 * @param message
	 *            The message to send
	 * @param blockingCommand
	 *            If not null then this latch is {@link BlockingCommand#complete(Throwable) complete} when processing the message is complete. The definition of
	 *            complete is:
	 *            <ul>
	 *            <li>If the message is a {@link ConnectMessage} processing is complete when the {@link ConnAckMessage} is received.</li>
	 *            <li>else if the message is {@link MqttMessage#isAckable() ackable} processing is complete when the ack is received.</li>
	 *            <li>else processing is complete when the message is written to the socket.</li>
	 *            <li>If any exception occurs or the channel is closed all in flight messages are complete</li>
	 *            </ul>
	 * 
	 * @return A return value of true does NOT necessarily mean this channel is open but false does mean it is closed (or the connect hasn't finished yet).
	 */
	boolean send(MqttMessage message, BlockingCommand<MqttMessage> blockingCommand);

	/**
	 * Writes as much data as possible. This should be called when a {@link SelectionKey}s {@link SelectionKey#OP_WRITE} op is ready.
	 * 
	 * @param now
	 *            The timestamp to use as the "current" time
	 * 
	 * @return True if the channel is left open. False if it is closed by this method or already closed when this method is called or the connect hasn't
	 *         finished yet.
	 */
	boolean write(long now);

	/**
	 * Closes the underlying channels, sockets, etc
	 */
	void close();

	/**
	 * Closes the underlying channels, sockets, etc and sends cause to the {@link MessageHandler#channelClosed(MqttChannel, Throwable)} callback.
	 */
	void close(Throwable cause);

	/**
	 * Tells whether or not this channel is open. This channel is open if the underlying channels, sockets, etc are open
	 * 
	 * @return true if, and only if, this channel is open
	 */
	boolean isOpen();

	/**
	 * Tells whether or not this channel is connected. This channel is connected if {@link #isOpen()} is true, Connect/ConnectAck has finished, and no
	 * disconnect has been received/sent.
	 * 
	 * @return True if and only if this channel is connected.
	 */
	boolean isConnected();

	/**
	 * Tells whether or not a connection operation is in progress on this channel.
	 * 
	 * @return true if, and only if, a connection operation has been initiated on this channel but not yet completed by invoking the {@link #finishConnect()}
	 *         method
	 */
	boolean isConnectionPending();

	/**
	 * Performs housekeeping: message resends, ping requests, etc
	 * 
	 * @param now
	 *            The timestamp to use as the "current" time
	 * 
	 * @return Maximum millis until this method should be called again. This result is only valid when this method is called. Future calls to
	 *         {@link #read(long)} or {@link #write(long)} may change this value. Returns < 0 if this method closes the channel.
	 */
	long houseKeeping(long now);

	/**
	 * @return The number of messages in the send queue. This includes any message currently in the process of being sent
	 */
	int sendQueueDepth();

	/**
	 * @return The number of messages currently in flight (QoS level > 0)
	 */
	int inFlightMessageCount();

	/**
	 * {@link BlockingCommand#cancel() Cancels} all blocking commands. This is not done when the channel is closed because we may want to reconnect instead of
	 * releasing the commands.
	 */
	void cancelBlockingCommands();

	/**
	 * @return All messages that have not been sent. This includes messages queued to be sent, any partially sent message, and all in flight messages.
	 */
	List<MqttMessage> getUnsentMessages();

	/**
	 * @return The channel's remote address
	 */
	String getRemoteAddress();

	/**
	 * @return The channel's local address
	 */
	String getLocalAddress();
}
