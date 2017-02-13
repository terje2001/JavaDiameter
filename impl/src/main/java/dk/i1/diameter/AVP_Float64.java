package dk.i1.diameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 64-bit floating point AVP
 */
public final class AVP_Float64 extends AVP {
  public AVP_Float64(final AVP a) throws InvalidAVPLengthException {
    super(a);
    if (a.queryPayloadSize() != 4) {
      throw new InvalidAVPLengthException(a);
    }
  }

  public AVP_Float64(final int code, final double value) {
    super(code, double2byte(value));
  }

  public AVP_Float64(final int code, final int vendor_id, final double value) {
    super(code, vendor_id, double2byte(value));
  }

  public void setValue(final double value) {
    setPayload(double2byte(value));
  }

  public double queryValue() {
    final byte v[] = queryPayload();
    final ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.BIG_ENDIAN);
    bb.put(v);
    bb.rewind();
    return bb.getDouble();
  }

  static private byte[] double2byte(final double value) {
    final ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.BIG_ENDIAN);
    bb.putDouble(value);
    bb.rewind();
    final byte v[] = new byte[4];
    bb.get(v);
    return v;
  }
}
