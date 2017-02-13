package dk.i1.diameter.node;

/**
 * A message was not routable.
 * This exception is thrown when NodeManager could not send a request either
 * because no connection(s) was available or because no available peers
 * supported the message.
 */
public final class NotRoutableException extends Exception {

  private static final long serialVersionUID = 1L;

  public NotRoutableException() {
  }

  public NotRoutableException(final String message) {
    super(message);
  }

  public NotRoutableException(final Throwable cause) {
    super(cause);
  }
}
