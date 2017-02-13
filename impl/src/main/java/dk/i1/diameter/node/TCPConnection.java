package dk.i1.diameter.node;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Collection;

final class TCPConnection extends Connection {
  TCPNode node_impl;
  SocketChannel channel;
  ConnectionBuffers connection_buffers;

  public TCPConnection(final TCPNode node_impl, final long watchdog_interval, final long idle_timeout) {
    super(node_impl, watchdog_interval, idle_timeout);
    this.node_impl = node_impl;
    connection_buffers = new NormalConnectionBuffers();
  }

  void makeSpaceInNetInBuffer() {
    connection_buffers.makeSpaceInNetInBuffer();
  }

  void makeSpaceInAppOutBuffer(final int how_much) {
    connection_buffers.makeSpaceInAppOutBuffer(how_much);
  }

  void consumeAppInBuffer(final int bytes) {
    connection_buffers.consumeAppInBuffer(bytes);
  }

  void consumeNetOutBuffer(final int bytes) {
    connection_buffers.consumeNetOutBuffer(bytes);
  }

  boolean hasNetOutput() {
    return connection_buffers.netOutBuffer().position() != 0;
  }

  void processNetInBuffer() {
    connection_buffers.processNetInBuffer();
  }

  void processAppOutBuffer() {
    connection_buffers.processAppOutBuffer();
  }

  @Override
  InetAddress toInetAddress() {
    return ((InetSocketAddress) (channel.socket().getRemoteSocketAddress())).getAddress();
  }

  @Override
  void sendMessage(final byte[] raw) {
    node_impl.sendMessage(this, raw);
  }

  @Override
  Object getRelevantNodeAuthInfo() {
    return channel;
  }

  @Override
  Collection<InetAddress> getLocalAddresses() {
    final Collection<InetAddress> coll = new ArrayList<InetAddress>();
    coll.add(channel.socket().getLocalAddress());
    return coll;
  }

  @Override
  Peer toPeer() {
    return new Peer(toInetAddress(), channel.socket().getPort());
  }
}
