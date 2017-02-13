package dk.i1.diameter.node;

/**
 * A peer hostname was empty.
 * This exception is thrown when trying to construct a {@link Peer} with an
 * empty hostname.
 */
public final class EmptyHostNameException extends Exception {

  private static final long serialVersionUID = 1L;

  public EmptyHostNameException() {
  }

  public EmptyHostNameException(final String message) {
    super(message);
  }

  public EmptyHostNameException(final Throwable cause) {
    super(cause);
  }
}
