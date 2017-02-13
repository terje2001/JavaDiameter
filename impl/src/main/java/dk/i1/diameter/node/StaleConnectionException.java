package dk.i1.diameter.node;

/**
 * A reference to a closed connection was detected.
 * This exception is thrown when Node detects a reference to a closed connection.
 */
public final class StaleConnectionException extends Exception {

  private static final long serialVersionUID = 1L;

  public StaleConnectionException() {
  }
}
