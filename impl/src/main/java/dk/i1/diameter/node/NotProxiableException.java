package dk.i1.diameter.node;

/**
 * A message was not proxiable.
 * This exception is thrown when forwarding a request or an answer
 * but the message was not marked as proxiable.
 * You probably forgot to check {@link dk.i1.diameter.MessageHeader#isProxiable}.
 */
public final class NotProxiableException extends Exception {

  private static final long serialVersionUID = 1L;

  public NotProxiableException() {
  }
}
