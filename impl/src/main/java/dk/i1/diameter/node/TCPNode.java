package dk.i1.diameter.node;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import dk.i1.diameter.Message;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class TCPNode extends NodeImplementation {

  private Thread node_thread;
  private Selector selector;
  private ServerSocketChannel serverChannel;
  private boolean please_stop;
  private long shutdown_deadline;

  public TCPNode(final Node node, final NodeSettings settings) {
    super(node, settings);
  }

  @Override
  void openIO() throws java.io.IOException {
    // create a new Selector for use below
    selector = Selector.open();
    if (settings.port() != 0) {
      // allocate an unbound server socket channel
      serverChannel = ServerSocketChannel.open();
      // Get the associated ServerSocket to bind it with
      final ServerSocket serverSocket = serverChannel.socket();
      // set the port the server channel will listen to
      serverSocket.bind(new InetSocketAddress(settings.port()));
    }
  }

  @Override
  void start() {
    log.trace("Starting TCP node");
    please_stop = false;
    node_thread = new SelectThread();
    node_thread.setDaemon(true);
    node_thread.start();
    log.trace("Started TCP node");
  }

  @Override
  void wakeup() {
    log.trace("Waking up selector thread");
    selector.wakeup();
  }

  @Override
  void initiateStop(final long shutdown_deadline) {
    log.trace("Initiating stop of TCP node");
    please_stop = true;
    this.shutdown_deadline = shutdown_deadline;
    log.trace("Initiated stop of TCP node");
  }

  @Override
  void join() {
    log.trace("Joining selector thread");
    try {
      node_thread.join();
    } catch (final InterruptedException ex) {
    }
    node_thread = null;
    log.trace("Selector thread joined");
  }

  @Override
  void closeIO() {
    log.trace("Closing server channel, etc.");
    if (serverChannel != null) {
      try {
        serverChannel.close();
      } catch (final java.io.IOException ex) {
      }
    }
    serverChannel = null;
    try {
      selector.close();
    } catch (final java.io.IOException ex) {
    }
    selector = null;
    log.trace("Closed selector, etc.");
  }

  private class SelectThread extends Thread {

    public SelectThread() {
      super("DiameterNode thread (TCP)");
    }

    @Override
    public void run() {
      try {
        run_();
        if (serverChannel != null) {
          serverChannel.close();
        }
      } catch (final java.io.IOException ex) {
      }
    }

    private void run_() throws java.io.IOException {
      if (serverChannel != null) {
        // set non-blocking mode for the listening socket
        serverChannel.configureBlocking(false);

        // register the ServerSocketChannel with the Selector
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
      }

      for (;;) {
        if (please_stop) {
          if (System.currentTimeMillis() >= shutdown_deadline) {
            break;
          }
          if (!anyOpenConnections()) {
            break;
          }
        }
        final long timeout = calcNextTimeout();
        int n;
        //System.out.println("selecting...");
        if (timeout != -1) {
          final long now = System.currentTimeMillis();
          if (timeout > now) {
            n = selector.select(timeout - now);
          } else {
            n = selector.selectNow();
          }
        } else {
          n = selector.select();
          //System.out.println("Woke up from select()");
        }

        // get an iterator over the set of selected keys
        final Iterator it = selector.selectedKeys().iterator();
        // look at each key in the selected set
        while (it.hasNext()) {
          final SelectionKey key = (SelectionKey) it.next();

          if (key.isAcceptable()) {
            log.trace("Got an inbound connection (key is acceptable)");
            final ServerSocketChannel server = (ServerSocketChannel) key.channel();
            final SocketChannel channel = server.accept();
            final InetSocketAddress address = (InetSocketAddress) channel.socket().getRemoteSocketAddress();
            if (log.isInfoEnabled()) {
              log.info("Got an inbound connection from " + address.toString());
            }
            if (!please_stop) {
              final TCPConnection conn
                      = new TCPConnection(TCPNode.this, settings.watchdogInterval(), settings.idleTimeout());
              conn.host_id = address.getAddress().getHostAddress();
              conn.state = Connection.State.connected_in;
              conn.channel = channel;
              channel.configureBlocking(false);
              channel.register(selector, SelectionKey.OP_READ, conn);

              registerInboundConnection(conn);
            } else {
              //We don't want to add the connection if were are shutting down.
              channel.close();
            }
          } else if (key.isConnectable()) {
            log.trace("An outbound connection is ready (key is connectable)");
            final SocketChannel channel = (SocketChannel) key.channel();
            final TCPConnection conn = (TCPConnection) key.attachment();
            try {
              if (channel.finishConnect()) {
                log.trace("Connected!");
                conn.state = Connection.State.connected_out;
                channel.register(selector, SelectionKey.OP_READ, conn);
                initiateCER(conn);
              }
            } catch (final java.io.IOException ex) {
              log.warn("Connection to '" + conn.host_id + "' failed", ex);
              try {
                channel.register(selector, 0);
                channel.close();
              } catch (final java.io.IOException ex2) {
              }
              unregisterConnection(conn);
            }
          } else if (key.isReadable()) {
            log.trace("Key is readable");
            //System.out.println("key is readable");
            final SocketChannel channel = (SocketChannel) key.channel();
            final TCPConnection conn = (TCPConnection) key.attachment();
            handleReadable(conn);
            if (conn.state != Connection.State.closed
                    && conn.hasNetOutput()) {
              channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, conn);
            }
          } else if (key.isWritable()) {
            log.trace("Key is writable");
            final SocketChannel channel = (SocketChannel) key.channel();
            final TCPConnection conn = (TCPConnection) key.attachment();
            synchronized (getLockObject()) {
              handleWritable(conn);
              if (conn.state != Connection.State.closed
                      && conn.hasNetOutput()) {
                channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, conn);
              }
            }
          }

          // remove key from selected set, it's been handled
          it.remove();
        }

        runTimers();
      }

      //Remaining connections are close by Node instance
      //selector is closed in stop()
    }
  }

  private void handleReadable(final TCPConnection conn) {
    log.trace("handlereadable()...");
    conn.makeSpaceInNetInBuffer();
    final ConnectionBuffers connection_buffers = conn.connection_buffers;
    if (log.isTraceEnabled()) {
      log.trace("pre: conn.in_buffer.position=" + connection_buffers.netInBuffer().position());
    }
    int count;
    try {
      int loop_count = 0;
      while ((count = conn.channel.read(connection_buffers.netInBuffer())) > 0 && loop_count++ < 3) {
        if (log.isTraceEnabled()) {
          log.trace("readloop: connection_buffers.netInBuffer().position=" + connection_buffers.netInBuffer().position());
        }
        conn.makeSpaceInNetInBuffer();
      }
    } catch (final java.io.IOException ex) {
      log.trace("got IOException", ex);
      closeConnection(conn);
      return;
    }
    conn.processNetInBuffer();
    processInBuffer(conn);
    if (count < 0 && conn.state != Connection.State.closed) {
      log.trace("count<0");
      closeConnection(conn);
      return;
    }
  }

  private void processInBuffer(final TCPConnection conn) {
    final ByteBuffer app_in_buffer = conn.connection_buffers.appInBuffer();
    if (log.isTraceEnabled()) {
      log.trace("pre: app_in_buffer.position=" + app_in_buffer.position());
    }
    final int raw_bytes = app_in_buffer.position();
    final byte[] raw = new byte[raw_bytes];
    app_in_buffer.position(0);
    app_in_buffer.get(raw);
    app_in_buffer.position(raw_bytes);
    int offset = 0;
    //System.out.println("processInBuffer():looping");
    while (offset < raw.length) {
      //System.out.println("processInBuffer(): inside loop offset=" + offset);
      final int bytes_left = raw.length - offset;
      if (bytes_left < 4) {
        break;
      }
      final int msg_size = Message.decodeSize(raw, offset);
      if (bytes_left < msg_size) {
        break;
      }
      final Message msg = new Message();
      final Message.decode_status status = msg.decode(raw, offset, msg_size);
      //System.out.println("processInBuffer():decoded, status=" + status);
      switch (status) {
        case decoded: {
          logRawDecodedPacket(raw, offset, msg_size);
          offset += msg_size;
          final boolean b = handleMessage(msg, conn);
          if (!b) {
            log.trace("handle error");
            closeConnection(conn);
            return;
          }
          break;
        }
        case not_enough:
          break;
        case garbage:
          logGarbagePacket(conn, raw, offset, msg_size);
          closeConnection(conn, true);
          return;
      }
      if (status == Message.decode_status.not_enough) {
        break;
      }
    }
    conn.consumeAppInBuffer(offset);
    //System.out.println("processInBuffer(): the end");
  }

  private void handleWritable(final Connection conn_) {
    final TCPConnection conn = (TCPConnection) conn_;
    log.trace("handleWritable():");
    final ByteBuffer net_out_buffer = conn.connection_buffers.netOutBuffer();
    //int bytes = net_out_buffer.position();
    //net_out_buffer.rewind();
    //net_out_buffer.limit(bytes);
    net_out_buffer.flip();
    //log.trace("                :bytes= " + bytes);
    int count;
    try {
      count = conn.channel.write(net_out_buffer);
      if (count < 0) {
        closeConnection(conn);
        return;
      }
      //conn.consumeNetOutBuffer(count);
      net_out_buffer.compact();
      conn.processAppOutBuffer();
      if (!conn.hasNetOutput()) {
        conn.channel.register(selector, SelectionKey.OP_READ, conn);
      }
    } catch (final java.io.IOException ex) {
      closeConnection(conn);
      return;
    }
  }

  void sendMessage(final TCPConnection conn, final byte[] raw) {
    final boolean was_empty = !conn.hasNetOutput();
    conn.makeSpaceInAppOutBuffer(raw.length);
    //System.out.println("sendMessage: A: position=" + out_buffer.position() + " limit=" + conn.out_buffer.limit());
    conn.connection_buffers.appOutBuffer().put(raw);
    conn.connection_buffers.processAppOutBuffer();
    //System.out.println("sendMessage: B: position=" + out_buffer.position() + " limit=" + conn.out_buffer.limit());

    if (was_empty) {
      outputBecameAvailable(conn);
    }
  }

  private void outputBecameAvailable(final Connection conn_) {
    final TCPConnection conn = (TCPConnection) conn_;
    handleWritable(conn);
    if (conn.hasNetOutput()) {
      try {
        conn.channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE, conn);
      } catch (final java.nio.channels.ClosedChannelException ex) {
      }
    }
  }

  @Override
  boolean initiateConnection(final Connection conn_, final Peer peer) {
    final TCPConnection conn = (TCPConnection) conn_;
    try {
      final SocketChannel channel = SocketChannel.open();
      channel.configureBlocking(false);
      final NodeSettings.PortRange port_range = settings.TCPPortRange();
      if (port_range != null) {
        bindChannelInRange(channel, port_range.min, port_range.max);
      }
      final InetSocketAddress address = new InetSocketAddress(peer.host(), peer.port());
      try {
        if (log.isTraceEnabled()) {
          log.trace("Initiating TCP connection to " + address.toString());
        }
        if (channel.connect(address)) {
          //This only happens on Solaris when connecting locally
          log.trace("Connected!");
          conn.state = Connection.State.connected_out;
          conn.channel = channel;
          selector.wakeup();
          channel.register(selector, SelectionKey.OP_READ, conn);
          initiateCER(conn);
          return true;
        }
      } catch (final java.nio.channels.UnresolvedAddressException ex) {
        channel.close();
        return false;
      }
      conn.state = Connection.State.connecting;
      conn.channel = channel;
      selector.wakeup();
      channel.register(selector, SelectionKey.OP_CONNECT, conn);
    } catch (final java.io.IOException ex) {
      log.warn("java.io.IOException caught while initiating connection to '" + peer.host() + "'.",
              ex);
    }
    return true;
  }

  @Override
  void close(final Connection conn_, final boolean reset) {
    final TCPConnection conn = (TCPConnection) conn_;
    try {
      conn.channel.register(selector, 0);
      if (reset) {
        //Set lingertime to zero to force a RST when closing the socket
        //rfc3588, section 2.1
        conn.channel.socket().setSoLinger(true, 0);
      }
      conn.channel.close();
    } catch (final java.io.IOException ex) {
    }
  }

  @Override
  Connection newConnection(final long watchdog_interval, final long idle_timeout) {
    return new TCPConnection(this, watchdog_interval, idle_timeout);
  }

  private static int last_tried_port = 0;

  private void bindChannelInRange(final SocketChannel channel, final int min, final int max)
          throws java.io.IOException {
    final int max_iterations = max - min + 1;
    for (int i = 0; i < max_iterations; i++) {
      last_tried_port++;
      if (last_tried_port < min) {
        last_tried_port = min;
      }
      if (last_tried_port > max) {
        last_tried_port = min;
      }
      try {
        channel.socket().bind(new InetSocketAddress(last_tried_port));
      } catch (final java.net.BindException ex) {
      }
      return;
    }
    throw new BindException("Could not bind socket in range " + min + "-" + max);
  }
}
