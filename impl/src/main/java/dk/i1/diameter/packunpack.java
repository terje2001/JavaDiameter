package dk.i1.diameter;

final class packunpack {
  public static void pack8(final byte b[], final int offset, final byte value) {
    b[offset] = value;
  }

  public static void pack16(final byte b[], final int offset, final int value) {
    b[offset + 0] = (byte) ((value >> 8) & 0xFF);
    b[offset + 1] = (byte) ((value) & 0xFF);
  }

  public static void pack32(final byte b[], final int offset, final int value) {
    b[offset + 0] = (byte) ((value >> 24) & 0xFF);
    b[offset + 1] = (byte) ((value >> 16) & 0xFF);
    b[offset + 2] = (byte) ((value >> 8) & 0xFF);
    b[offset + 3] = (byte) ((value) & 0xFF);
  }

  public static void pack64(final byte b[], final int offset, final long value) {
    b[offset + 0] = (byte) ((value >> 56) & 0xFF);
    b[offset + 1] = (byte) ((value >> 48) & 0xFF);
    b[offset + 2] = (byte) ((value >> 40) & 0xFF);
    b[offset + 3] = (byte) ((value >> 32) & 0xFF);
    b[offset + 4] = (byte) ((value >> 24) & 0xFF);
    b[offset + 5] = (byte) ((value >> 16) & 0xFF);
    b[offset + 6] = (byte) ((value >> 8) & 0xFF);
    b[offset + 7] = (byte) ((value) & 0xFF);
  }

  public static byte unpack8(final byte b[], final int offset) {
    return b[offset];
  }

  public static int unpack32(final byte b[], final int offset) {
    return ((b[offset + 0] & 0xFF) << 24)
        | ((b[offset + 1] & 0xFF) << 16)
        | ((b[offset + 2] & 0xFF) << 8)
        | ((b[offset + 3] & 0xFF));
  }

  public static int unpack16(final byte b[], final int offset) {
    return ((b[offset + 0] & 0xFF) << 8)
        | ((b[offset + 1] & 0xFF));
  }

  public static long unpack64(final byte b[], final int offset) {
    return ((long) (b[offset + 0] & 0xFF) << 56)
        | ((long) (b[offset + 1] & 0xFF) << 48)
        | ((long) (b[offset + 2] & 0xFF) << 40)
        | ((long) (b[offset + 3] & 0xFF) << 32)
        | ((long) (b[offset + 4] & 0xFF) << 24)
        | ((long) (b[offset + 5] & 0xFF) << 16)
        | ((long) (b[offset + 6] & 0xFF) << 8)
        | (b[offset + 7] & 0xFF);
  }
}
