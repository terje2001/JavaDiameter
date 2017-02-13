package dk.i1.diameter;

/**
 * 64-bit unsigned integer AVP.
 * RFC3855 describes the Unsigned64 AVP type. Java does not have an appropriate
 * unsigned data type, so this class is functionally equivalent to {@link AVP_Integer64}
 */
public final class AVP_Unsigned64 extends AVP {
  public AVP_Unsigned64(final AVP a) throws InvalidAVPLengthException {
    super(a);
    if (a.queryPayloadSize() != 8) {
      throw new InvalidAVPLengthException(a);
    }
  }

  public AVP_Unsigned64(final int code, final long value) {
    super(code, long2byte(value));
  }

  public AVP_Unsigned64(final int code, final int vendor_id, final long value) {
    super(code, vendor_id, long2byte(value));
  }

  public long queryValue() {
    return packunpack.unpack64(payload, 0);
  }

  public void setValue(final long value) {
    packunpack.pack64(payload, 0, value);
  }

  static private byte[] long2byte(final long value) {
    final byte[] v = new byte[8];
    packunpack.pack64(v, 0, value);
    return v;
  }
}
