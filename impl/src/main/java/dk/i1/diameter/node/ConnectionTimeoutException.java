package dk.i1.diameter.node;

/**
 * ConnectionTimeout exception.
 * This exception is thrown when {@link dk.i1.diameter.node.Node#waitForConnectionTimeout} or
 * {@link dk.i1.diameter.node.NodeManager#waitForConnectionTimeout} times out.
 */
public final class ConnectionTimeoutException extends java.util.concurrent.TimeoutException {

  private static final long serialVersionUID = 1L;

  public ConnectionTimeoutException(final String message) {
    super(message);
  }
}
