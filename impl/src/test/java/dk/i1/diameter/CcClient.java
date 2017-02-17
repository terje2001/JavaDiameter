package dk.i1.diameter;

import dk.i1.diameter.node.Capability;
import dk.i1.diameter.node.EmptyHostNameException;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.Peer;
import dk.i1.diameter.node.SimpleSyncClient;
import dk.i1.diameter.node.UnsupportedTransportProtocolException;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;

/**
 * Test Client for junit testing. Copy and paste, with lots of modification, of the cc_test_client.java, from original project.
 */
@Slf4j
final class CcClient {

  private CcClient() {
    // Util class, so private empty constructor
  }

  static boolean doCcRequestResponse(final String hostId, final String realm, final String destHost, final int destPort, final boolean useTcp)
          throws EmptyHostNameException, IOException, InterruptedException, UnsupportedTransportProtocolException {

    final Capability capability = new Capability();
    capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL);
    //capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL);

    final NodeSettings nodeSettings;
    try {
      nodeSettings = new NodeSettings(
              hostId, realm,
              99999, //vendor-id
              capability,
              0,
              "cc_test_client", 0x01000000);
    } catch (final InvalidSettingException ex) {
      throw new AssertionError("Unexpected exception: " + ex, ex);
    }
    nodeSettings.setUseTCP(useTcp);
    nodeSettings.setUseSCTP(!useTcp);

    final Peer[] peers = new Peer[]{
      new Peer(destHost, destPort, useTcp ? Peer.TransportProtocol.tcp : Peer.TransportProtocol.sctp)
    };

    final SimpleSyncClient ssc = new SimpleSyncClient(nodeSettings, peers);
    final boolean result;
    try {
      ssc.start();
      ssc.waitForConnection(); //allow connection to be established.

      //Build Credit-Control Request
      // <Credit-Control-Request> ::= < Diameter Header: 272, REQ, PXY >
      final Message ccr = new Message();
      ccr.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_CC;
      ccr.hdr.application_id = ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL;
      ccr.hdr.setRequest(true);
      ccr.hdr.setProxiable(true);
      //  < Session-Id >
      ccr.add(new AVP_UTF8String(ProtocolConstants.DI_SESSION_ID, ssc.node().makeNewSessionId()));
      //  { Origin-Host }
      //  { Origin-Realm }
      ssc.node().addOurHostAndRealm(ccr);
      //  { Destination-Realm }
      ccr.add(new AVP_UTF8String(ProtocolConstants.DI_DESTINATION_REALM, "example.net"));
      //  { Auth-Application-Id }
      ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID,
              ProtocolConstants.DIAMETER_APPLICATION_CREDIT_CONTROL)); // a lie but a minor one
      //  { Service-Context-Id }
      ccr.add(new AVP_UTF8String(ProtocolConstants.DI_SERVICE_CONTEXT_ID, "cc_test@example.net"));
      //  { CC-Request-Type }
      ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_CC_REQUEST_TYPE, ProtocolConstants.DI_CC_REQUEST_TYPE_EVENT_REQUEST));

      //  { CC-Request-Number }
      ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_CC_REQUEST_NUMBER, 0));
      //  [ Destination-Host ]
      //  [ User-Name ]
      ccr.add(new AVP_UTF8String(ProtocolConstants.DI_USER_NAME, "user@example.net"));
      //  [ CC-Sub-Session-Id ]
      //  [ Acct-Multi-Session-Id ]
      //  [ Origin-State-Id ]
      ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_ORIGIN_STATE_ID, ssc.node().stateId()));
      //  [ Event-Timestamp ]
      ccr.add(new AVP_Time(ProtocolConstants.DI_EVENT_TIMESTAMP, (int) (System.currentTimeMillis() / 1000)));
      // *[ Subscription-Id ]
      //  [ Service-Identifier ]
      //  [ Termination-Cause ]
      //  [ Requested-Service-Unit ]
      ccr.add(new AVP_Grouped(ProtocolConstants.DI_REQUESTED_SERVICE_UNIT,
              new AVP[]{new AVP_Unsigned64(ProtocolConstants.DI_CC_SERVICE_SPECIFIC_UNITS, 42)}));
      //  [ Requested-Action ]
      ccr.add(new AVP_Unsigned32(ProtocolConstants.DI_REQUESTED_ACTION,
              ProtocolConstants.DI_REQUESTED_ACTION_DIRECT_DEBITING));
      // *[ Used-Service-Unit ]
      //  [ Multiple-Services-Indicator ]
      // *[ Multiple-Services-Credit-Control ]
      // *[ Service-Parameter-Info ]
      ccr.add(new AVP_Grouped(ProtocolConstants.DI_SERVICE_PARAMETER_INFO,
              new AVP[]{new AVP_Unsigned32(ProtocolConstants.DI_SERVICE_PARAMETER_TYPE, 42),
                new AVP_UTF8String(ProtocolConstants.DI_SERVICE_PARAMETER_VALUE, "Hovercraft")
              }));
      //  [ CC-Correlation-Id ]
      //  [ User-Equipment-Info ]
      // *[ Proxy-Info ]
      // *[ Route-Record ]
      // *[ AVP ]

      Utils.setMandatory_RFC3588(ccr);
      Utils.setMandatory_RFC4006(ccr);

      //Send it
      final Message cca = ssc.sendRequest(ccr);

      //Now look at the result
      if (cca == null) {
        throw new AssertionError("No response");
      }
      final AVP resultCode = cca.find(ProtocolConstants.DI_RESULT_CODE);
      if (resultCode == null) {
        throw new AssertionError("No result code");
      }
      try {
        final AVP_Unsigned32 resultCodeU32 = new AVP_Unsigned32(resultCode);
        final int rc = resultCodeU32.queryValue();
        if (rc == ProtocolConstants.DIAMETER_RESULT_SUCCESS) {
          result = true;
        } else {
          throw new AssertionError("Incorrect result code: " + rc);
        }
      } catch (final InvalidAVPLengthException ex) {
        throw new AssertionError("result-code was illformed: " + ex, ex);
      }
    } finally {
      //Stop the stack
      ssc.stop();
    }

    return result;
  }
}
