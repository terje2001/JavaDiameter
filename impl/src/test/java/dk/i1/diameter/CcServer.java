package dk.i1.diameter;

import dk.i1.diameter.node.Capability;
import dk.i1.diameter.node.ConnectionKey;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeManager;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.NotAnAnswerException;
import dk.i1.diameter.node.Peer;

/**
 * Test Server for junit testing. Copy and paste, with lots of modification, of the cc_test_server.java, from original project.
 */
final class CcServer extends NodeManager {

  private CcServer(final NodeSettings nodeSettings) {
    super(nodeSettings);
  }

  public static CcServer create(final String hostId, final String realm, final int port, final boolean useTcp) {
    //  port = 3868;

    final Capability capability = new Capability();
    capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL);

    final NodeSettings nodeSettings;
    try {
      nodeSettings = new NodeSettings(
              hostId, realm,
              99999, //vendor-id
              capability,
              port,
              "cc_test_server", 0x01000000);
    } catch (final InvalidSettingException ex) {
      throw new AssertionError("Unexpected exception: " + ex, ex);
    }
    nodeSettings.setUseTCP(useTcp);
    nodeSettings.setUseSCTP(!useTcp);

    final CcServer tss = new CcServer(nodeSettings);

    return tss;
  }

  @Override
  protected void handleRequest(final Message request, final ConnectionKey connkey, final Peer peer) {
    //this is not the way to do it, but fine for a lean-and-mean test server
    final Message answer = new Message();
    answer.prepareResponse(request);
    AVP avp;
    avp = request.find(ProtocolConstants.DI_SESSION_ID);
    if (avp != null) {
      answer.add(avp);
    }
    node().addOurHostAndRealm(answer);
    avp = request.find(ProtocolConstants.DI_CC_REQUEST_TYPE);
    if (avp == null) {
      answerError(answer, connkey, ProtocolConstants.DIAMETER_RESULT_MISSING_AVP,
              new AVP[]{new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,
                        new AVP[]{new AVP(ProtocolConstants.DI_CC_REQUEST_TYPE, new byte[]{})})});
      return;
    }
    final int ccRequestType; // = -1;
    try {
      ccRequestType = new AVP_Unsigned32(avp).queryValue();
    } catch (final InvalidAVPLengthException ex) {
      throw new AssertionError("Unexpected exception: " + ex, ex);
    }
    if (ccRequestType != ProtocolConstants.DI_CC_REQUEST_TYPE_INITIAL_REQUEST
            && ccRequestType != ProtocolConstants.DI_CC_REQUEST_TYPE_UPDATE_REQUEST
            && ccRequestType != ProtocolConstants.DI_CC_REQUEST_TYPE_TERMINATION_REQUEST
            && ccRequestType != ProtocolConstants.DI_CC_REQUEST_TYPE_EVENT_REQUEST) {
      answerError(answer, connkey, ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
              new AVP[]{new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP, new AVP[]{avp})});
      return;
    }

    //This test server does not support multiple-services-cc
    avp = request.find(ProtocolConstants.DI_MULTIPLE_SERVICES_CREDIT_CONTROL);
    if (avp != null) {
      answerError(answer, connkey, ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
              new AVP[]{new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP, new AVP[]{avp})});
      return;
    }
    avp = request.find(ProtocolConstants.DI_MULTIPLE_SERVICES_INDICATOR);
    if (avp != null) {
      int indicator = -1;
      try {
        indicator = new AVP_Unsigned32(avp).queryValue();
      } catch (final InvalidAVPLengthException ex) {
        throw new AssertionError("Unexpected exception: " + ex, ex);
      }
      if (indicator != ProtocolConstants.DI_MULTIPLE_SERVICES_INDICATOR_MULTIPLE_SERVICES_NOT_SUPPORTED) {
        answerError(answer, connkey, ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
                new AVP[]{new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP, new AVP[]{avp})});
        return;
      }
    }

    answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_SUCCESS));
    avp = request.find(ProtocolConstants.DI_AUTH_APPLICATION_ID);
    if (avp != null) {
      answer.add(avp);
    }
    avp = request.find(ProtocolConstants.DI_CC_REQUEST_TYPE);
    if (avp != null) {
      answer.add(avp);
    }
    avp = request.find(ProtocolConstants.DI_CC_REQUEST_NUMBER);
    if (avp != null) {
      answer.add(avp);
    }

    switch (ccRequestType) {
      case ProtocolConstants.DI_CC_REQUEST_TYPE_INITIAL_REQUEST:
      case ProtocolConstants.DI_CC_REQUEST_TYPE_UPDATE_REQUEST:
      case ProtocolConstants.DI_CC_REQUEST_TYPE_TERMINATION_REQUEST:
        //grant whatever is requested
        avp = request.find(ProtocolConstants.DI_REQUESTED_SERVICE_UNIT);
        if (avp != null) {
          final AVP g = new AVP(avp);
          g.code = ProtocolConstants.DI_GRANTED_SERVICE_UNIT;
          answer.add(avp);
        }
        break;
      case ProtocolConstants.DI_CC_REQUEST_TYPE_EVENT_REQUEST:
        //examine requested-action
        avp = request.find(ProtocolConstants.DI_REQUESTED_ACTION);
        if (avp == null) {
          answerError(answer, connkey, ProtocolConstants.DIAMETER_RESULT_MISSING_AVP,
                  new AVP[]{new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,
                            new AVP[]{new AVP(ProtocolConstants.DI_REQUESTED_ACTION, new byte[]{})})});
          return;
        }
        int requestedAction = -1;
        try {
          requestedAction = new AVP_Unsigned32(avp).queryValue();
        } catch (final InvalidAVPLengthException ex) {
          throw new AssertionError("Unexpected exception: " + ex, ex);
        }
        switch (requestedAction) {
          case ProtocolConstants.DI_REQUESTED_ACTION_DIRECT_DEBITING:
            //nothing. just indicate success
            break;
          case ProtocolConstants.DI_REQUESTED_ACTION_REFUND_ACCOUNT:
            //nothing. just indicate success
            break;
          case ProtocolConstants.DI_REQUESTED_ACTION_CHECK_BALANCE:
            //report back that the user has sufficient balance
            answer.add(new AVP_Unsigned32(ProtocolConstants.DI_CHECK_BALANCE_RESULT,
                    ProtocolConstants.DI_DI_CHECK_BALANCE_RESULT_ENOUGH_CREDIT));
            break;
          case ProtocolConstants.DI_REQUESTED_ACTION_PRICE_ENQUIRY:
            //report back a price of DKK42.17 per kanelsnegl
            answer.add(new AVP_Grouped(ProtocolConstants.DI_COST_INFORMATION,
                    new AVP[]{new AVP_Grouped(ProtocolConstants.DI_UNIT_VALUE,
                              new AVP[]{new AVP_Integer64(ProtocolConstants.DI_VALUE_DIGITS, 4217),
                                new AVP_Integer32(ProtocolConstants.DI_EXPONENT, -2)
                              }),
                      new AVP_Unsigned32(ProtocolConstants.DI_CURRENCY_CODE, 208),
                      new AVP_UTF8String(ProtocolConstants.DI_COST_UNIT, "kanelsnegl")
                    }));
            break;
          default: {
            answerError(answer, connkey, ProtocolConstants.DIAMETER_RESULT_INVALID_AVP_VALUE,
                    new AVP[]{new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP, new AVP[]{avp})});
            return;
          }

        }
        break;
      default:
        throw new AssertionError("Unexpected value:" + ccRequestType);
    }

    Utils.setMandatory_RFC3588(answer);

    try {
      answer(answer, connkey);
    } catch (final NotAnAnswerException ex) {
      throw new AssertionError("Unexpected exception: " + ex, ex);
    }
  }

  void answerError(final Message answer, final ConnectionKey connkey, final int resultCode, final AVP[] errorAvp) {
    answer.hdr.setError(true);
    answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, resultCode));
    for (final AVP avp : errorAvp) {
      answer.add(avp);
    }
    try {
      answer(answer, connkey);
    } catch (final NotAnAnswerException ex) {
      throw new AssertionError("Unexpected exception: " + ex, ex);
    }
  }
}
