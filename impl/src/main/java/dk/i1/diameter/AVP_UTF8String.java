package dk.i1.diameter;

/**
 * AVP with UTF-8 string payload.
 */
public final class AVP_UTF8String extends AVP {
  public AVP_UTF8String(final AVP a) {
    super(a);
  }

  public AVP_UTF8String(final int code, final String value) {
    super(code, string2byte(value));
  }

  public AVP_UTF8String(final int code, final int vendor_id, final String value) {
    super(code, vendor_id, string2byte(value));
  }

  public String queryValue() {
    try {
      return new String(queryPayload(), "UTF-8");
    } catch (final java.io.UnsupportedEncodingException e) {
      return null;
    }
  }

  public void setValue(final String value) {
    setPayload(string2byte(value));
  }

  static private byte[] string2byte(final String value) {
    try {
      return value.getBytes("UTF-8");
    } catch (final java.io.UnsupportedEncodingException e) {
      //never happens
    }
    return null;
  }
}
