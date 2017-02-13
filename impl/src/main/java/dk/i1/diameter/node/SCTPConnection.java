package dk.i1.diameter.node;

import java.net.InetAddress;
import java.util.Collection;
import java.util.LinkedList;
import dk.i1.sctp.AssociationId;

final class SCTPConnection extends Connection {
  //Queue of pending messages
  private final LinkedList<byte[]> queued_messages;
  private final SCTPNode node_impl;
  AssociationId assoc_id;
  boolean closed;
  short sac_inbound_streams;
  short sac_outbound_streams;
  short out_stream_index;

  SCTPConnection(final SCTPNode node_impl, final long watchdog_interval, final long idle_timeout) {
    super(node_impl, watchdog_interval, idle_timeout);
    queued_messages = new LinkedList<byte[]>();
    this.node_impl = node_impl;
    this.closed = false;
    this.sac_inbound_streams = 0;
    this.sac_outbound_streams = 0;
    this.out_stream_index = 0;
  }

  //Return the next stream number to use for sending
  short nextOutStream() {
    final short i = out_stream_index;
    out_stream_index = (short) ((out_stream_index + 1) % sac_outbound_streams);
    return i;
  }

  @Override
  InetAddress toInetAddress() {
    final Collection<InetAddress> coll = getLocalAddresses();
    for (final InetAddress ia : coll) {
      return ia;
    }
    return null;
  }

  @Override
  void sendMessage(final byte[] raw) {
    node_impl.sendMessage(this, raw);
  }

  @Override
  Object getRelevantNodeAuthInfo() {
    return new RelevantSCTPAuthInfo(node_impl.sctp_socket, assoc_id);
  }

  @Override
  Collection<InetAddress> getLocalAddresses() {
    try {
      return node_impl.sctp_socket.getLocalInetAddresses(assoc_id);
    } catch (final java.net.SocketException ex) {
      return null;
    }
  }

  @Override
  Peer toPeer() {
    try {
      return new Peer(toInetAddress(), node_impl.sctp_socket.getPeerInetPort(assoc_id));
    } catch (final java.net.SocketException ex) {
      return null;
    }
  }

  void queueMessage(final byte[] raw) {
    queued_messages.addLast(raw);
  }

  byte[] peekFirstQueuedMessage() {
    return queued_messages.peek();
  }

  void removeFirstQueuedMessage() {
    queued_messages.poll();
  }
}
