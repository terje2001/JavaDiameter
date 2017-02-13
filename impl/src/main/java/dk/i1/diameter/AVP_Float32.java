package dk.i1.diameter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 32-bit floating point AVP
 */
public final class AVP_Float32 extends AVP {
  public AVP_Float32(final AVP a) throws InvalidAVPLengthException {
    super(a);
    if (a.queryPayloadSize() != 4) {
      throw new InvalidAVPLengthException(a);
    }
  }

  public AVP_Float32(final int code, final float value) {
    super(code, float2byte(value));
  }

  public AVP_Float32(final int code, final int vendor_id, final float value) {
    super(code, vendor_id, float2byte(value));
  }

  public void setValue(final float value) {
    setPayload(float2byte(value));
  }

  public float queryValue() {
    final byte v[] = queryPayload();
    final ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.BIG_ENDIAN);
    bb.put(v);
    bb.rewind();
    return bb.getFloat();
  }

  static private byte[] float2byte(final float value) {
    final ByteBuffer bb = ByteBuffer.allocate(4);
    bb.order(ByteOrder.BIG_ENDIAN);
    bb.putFloat(value);
    bb.rewind();
    final byte v[] = new byte[4];
    bb.get(v);
    return v;
  }
}
