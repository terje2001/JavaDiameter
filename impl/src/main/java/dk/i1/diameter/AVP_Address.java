package dk.i1.diameter;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * An internet address AVP.
 * This class reflects the Address type AVP described in RFC3588.
 * It supports both IPv4 and IPv6.
 * <p>
 * <em>Note:</em>Values not conforming to RFC3588 has been seen in the wild.
 */
public final class AVP_Address extends AVP_OctetString {
  public AVP_Address(final AVP a) throws InvalidAVPLengthException, InvalidAddressTypeException {
    super(a);
    if (a.queryPayloadSize() < 2) {
      throw new InvalidAVPLengthException(a);
    }
    final int address_family = packunpack.unpack16(payload, 0);
    if (address_family == 1) {
      if (a.queryPayloadSize() != 2 + 4) {
        throw new InvalidAVPLengthException(a);
      }
    } else if (address_family == 2) {
      if (a.queryPayloadSize() != 2 + 16) {
        throw new InvalidAVPLengthException(a);
      }
    } else {
      throw new InvalidAddressTypeException(a);
    }
  }

  public AVP_Address(final int code, final InetAddress ia) {
    super(code, InetAddress2byte(ia));
  }

  public AVP_Address(final int code, final int vendor_id, final InetAddress ia) {
    super(code, vendor_id, InetAddress2byte(ia));
  }

  public InetAddress queryAddress() throws InvalidAVPLengthException, InvalidAddressTypeException {
    if (queryPayloadSize() < 2) {
      throw new InvalidAVPLengthException(this);
    }
    final byte raw[] = queryValue();
    final int address_family = packunpack.unpack16(raw, 0);
    try {
      switch (address_family) {
        case 1: {
          if (queryPayloadSize() != 2 + 4) {
            throw new InvalidAVPLengthException(this);
          }
          final byte tmp[] = new byte[4];
          System.arraycopy(raw, 2, tmp, 0, 4);
          return InetAddress.getByAddress(tmp);
        }
        case 2: {
          if (queryPayloadSize() != 2 + 16) {
            throw new InvalidAVPLengthException(this);
          }
          final byte tmp[] = new byte[16];
          System.arraycopy(raw, 2, tmp, 0, 16);
          return InetAddress.getByAddress(tmp);
        }
        default:
          throw new InvalidAddressTypeException(this);

      }
    } catch (final java.net.UnknownHostException e) {
    }
    return null; //never reached
  }

  public void setAddress(final InetAddress ia) {
    setValue(InetAddress2byte(ia));
  }

  static private byte[] InetAddress2byte(final InetAddress ia) {
    final byte raw_address[] = ia.getAddress();
    int address_family;
    try {
      final Inet4Address i4a = (Inet4Address) (ia);
      address_family = 1; //http://www.iana.org/assignments/address-family-numbers
    } catch (final ClassCastException e) {
      final Inet6Address i6a = (Inet6Address) (ia);
      address_family = 2;
    }

    final byte raw[] = new byte[2 + raw_address.length];
    packunpack.pack16(raw, 0, address_family);
    System.arraycopy(raw_address, 0, raw, 2, raw_address.length);
    return raw;
  }
}
