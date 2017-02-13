package dk.i1.diameter;

/**
 * Exception thrown when an AVP_Address is constructed from unsupported on-the-wire content.
 */
public final class InvalidAddressTypeException extends Exception {

  private static final long serialVersionUID = 1L;

  /**
   * The AVP that did not have the correct size/type of its expected type. This can later be wrapped into an Failed-AVP
   * AVP.
   */
  public AVP avp;

  /** Construct the expection with the specified AVP */
  public InvalidAddressTypeException(final AVP avp) {
    this.avp = new AVP(avp);
  }
}
