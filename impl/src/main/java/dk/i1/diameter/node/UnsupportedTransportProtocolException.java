package dk.i1.diameter.node;

/**
 * Unsupported transport protocol exception.
 * This exception is thrown when {@link Node#start} is called and one of the
 * mandatory transport protocols are not supported.
 */
public final class UnsupportedTransportProtocolException extends Exception {

  private static final long serialVersionUID = 1L;

  public UnsupportedTransportProtocolException(final String message) {
    super(message);
  }

  public UnsupportedTransportProtocolException(final String message, final Throwable ex) {
    super(message, ex);
  }
}
