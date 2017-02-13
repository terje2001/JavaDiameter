package dk.i1.diameter;

/**
 * 32-bit signed integer AVP.
 */
public final class AVP_Integer32 extends AVP {
  public AVP_Integer32(final AVP a) throws InvalidAVPLengthException {
    super(a);
    if (a.queryPayloadSize() != 4) {
      throw new InvalidAVPLengthException(a);
    }
  }

  public AVP_Integer32(final int code, final int value) {
    super(code, int2byte(value));
  }

  public AVP_Integer32(final int code, final int vendor_id, final int value) {
    super(code, vendor_id, int2byte(value));
  }

  public int queryValue() {
    return packunpack.unpack32(payload, 0);
  }

  public void setValue(final int value) {
    packunpack.pack32(payload, 0, value);
  }

  static private byte[] int2byte(final int value) {
    final byte[] v = new byte[4];
    packunpack.pack32(v, 0, value);
    return v;
  }
}
