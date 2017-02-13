package dk.i1.diameter.session;

public final class InvalidStateException extends Exception {

  private static final long serialVersionUID = 1L;

  public InvalidStateException() {
  }

  public InvalidStateException(final String message) {
    super(message);
  }

  public InvalidStateException(final Throwable cause) {
    super(cause);
  }
}
