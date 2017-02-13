package dk.i1.diameter.node;

/**
 * Invalid NodeSettings exception.
 * This exception is thrown when {@link NodeSettings} or
 * {@link dk.i1.diameter.session.SessionManager} detects an invalid setting.
 */
public final class InvalidSettingException extends Exception {

  private static final long serialVersionUID = 1L;

  public InvalidSettingException(final String message) {
    super(message);
  }
}
