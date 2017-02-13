package dk.i1.diameter.node;

import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_Grouped;
import dk.i1.diameter.ProtocolConstants;

final class AVP_FailedAVP extends AVP_Grouped {
  private static AVP[] wrap(final AVP a) {
    final AVP g[] = new AVP[1];
    g[0] = a;
    return g;
  }

  public AVP_FailedAVP(final AVP a) {
    super(ProtocolConstants.DI_FAILED_AVP, wrap(a));
  }
}
