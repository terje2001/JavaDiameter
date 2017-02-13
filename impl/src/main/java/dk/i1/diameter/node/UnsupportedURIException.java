package dk.i1.diameter.node;

import java.net.URI;

/**
 * Thrown when giving {@link Peer#Peer(URI)} or {@link Peer#fromURIString(String)} an unsupported URI.
 */
public final class UnsupportedURIException extends Exception {

  private static final long serialVersionUID = 1L;

  public UnsupportedURIException(final String message) {
    super(message);
  }

  public UnsupportedURIException(final Throwable cause) {
    super(cause);
  }
}
