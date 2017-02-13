package dk.i1.diameter.node;

import dk.i1.diameter.AVP;

final class InvalidAVPValueException extends Exception {

  private static final long serialVersionUID = 1L;

  public AVP avp;

  public InvalidAVPValueException(final AVP avp) {
    this.avp = avp;
  }
}
