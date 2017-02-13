package dk.i1.diameter.node;

import java.util.logging.Logger;
import dk.i1.diameter.Message;

/**
 * Base class for transport-specific implementations.
 * This class acts a a common superclass for the TCPNode and SCTPNode classes.
 */
abstract class NodeImplementation {
  private final Node node;
  protected NodeSettings settings;
  protected Logger logger;

  NodeImplementation(final Node node, final NodeSettings settings, final Logger logger) {
    this.node = node;
    this.settings = settings;
    this.logger = logger;
  }

  abstract void openIO() throws java.io.IOException;

  abstract void start();

  abstract void wakeup();

  abstract void initiateStop(long shutdown_deadline);

  abstract void join();

  abstract void closeIO();

  abstract boolean initiateConnection(Connection conn, Peer peer);

  abstract void close(Connection conn, boolean reset);

  abstract Connection newConnection(long watchdog_interval, long idle_timeout);

  //Helper functions. Mostly forwarders to node
  boolean anyOpenConnections() {
    return node.anyOpenConnections(this);
  }

  void registerInboundConnection(final Connection conn) {
    node.registerInboundConnection(conn);
  }

  void unregisterConnection(final Connection conn) {
    node.unregisterConnection(conn);
  }

  long calcNextTimeout() {
    return node.calcNextTimeout(this);
  }

  void closeConnection(final Connection conn) {
    node.closeConnection(conn);
  }

  void closeConnection(final Connection conn, final boolean reset) {
    node.closeConnection(conn, reset);
  }

  boolean handleMessage(final Message msg, final Connection conn) {
    return node.handleMessage(msg, conn);
  }

  void runTimers() {
    node.runTimers(this);
  }

  void logRawDecodedPacket(final byte[] raw, final int offset, final int msg_size) {
    node.logRawDecodedPacket(raw, offset, msg_size);
  }

  void logGarbagePacket(final Connection conn, final byte[] raw, final int offset, final int msg_size) {
    node.logGarbagePacket(conn, raw, offset, msg_size);
  }

  Object getLockObject() {
    return node.getLockObject();
  }

  void initiateCER(final Connection conn) {
    node.initiateCER(conn);
  }
}
