package dk.i1.diameter;

/**
 * Exception thrown when an AVP does not have the correct size.
 */
public final class InvalidAVPLengthException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * The AVP that did not have the correct size of its expected type. This can later be wrapped into an Failed-AVP AVP.
   */
  public AVP avp;

  /** Construct the expection with the specified AVP */
  public InvalidAVPLengthException(final AVP avp) {
    this.avp = new AVP(avp);
  }
}
