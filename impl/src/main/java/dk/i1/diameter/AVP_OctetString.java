package dk.i1.diameter;

/**
 * AVP containing arbitrary data of variable length.
 */
public class AVP_OctetString extends AVP {
  public AVP_OctetString(final AVP avp) {
    super(avp);
  }

  public AVP_OctetString(final int code, final byte value[]) {
    super(code, value);
  }

  public AVP_OctetString(final int code, final int vendor_id, final byte value[]) {
    super(code, vendor_id, value);
  }

  public byte[] queryValue() {
    return queryPayload();
  }

  public void setValue(final byte value[]) {
    setPayload(value, 0, value.length);
  }
}
