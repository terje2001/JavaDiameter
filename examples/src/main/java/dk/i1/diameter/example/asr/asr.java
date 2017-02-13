package dk.i1.diameter.example.asr;

import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_UTF8String;
import dk.i1.diameter.AVP_Unsigned32;
import dk.i1.diameter.Message;
import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.Utils;
import dk.i1.diameter.node.Capability;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.Peer;
import dk.i1.diameter.node.SimpleSyncClient;

/**
 * A simple client that issues an ASR (Abort-Session-Request).
 */

final class asr {
  public static void main(final String args[]) throws Exception {
    if (args.length != 3) {
      System.out.println("Usage: <peer> <auth-app-id> <session-id>");
      return;
    }

    final String peer = args[0];
    int auth_app_id;
    if (args[1].equals("nasreq")) {
      auth_app_id = ProtocolConstants.DIAMETER_APPLICATION_NASREQ;
    } else if (args[1].equals("mobileip")) {
      auth_app_id = ProtocolConstants.DIAMETER_APPLICATION_MOBILEIP;
    } else {
      auth_app_id = Integer.valueOf(args[1]);
    }
    final String session_id = args[2];
    final String dest_host = args[0];
    final String dest_realm = dest_host.substring(dest_host.indexOf('.') + 1);

    final Capability capability = new Capability();
    capability.addAuthApp(auth_app_id);

    NodeSettings node_settings;
    try {
      node_settings = new NodeSettings(
          "somehost.example.net", "example.net",
          99999, //vendor-id
          capability,
          9999,
          "ASR client", 0x01000000);
    } catch (final InvalidSettingException e) {
      System.out.println(e.toString());
      return;
    }

    final Peer peers[] = new Peer[] {
      new Peer(peer)
    };

    final SimpleSyncClient ssc = new SimpleSyncClient(node_settings, peers);
    ssc.start();
    Thread.sleep(2000); //allow connections to be established.

    //Build ASR
    final Message asr = new Message();
    asr.add(new AVP_UTF8String(ProtocolConstants.DI_SESSION_ID, session_id));
    ssc.node().addOurHostAndRealm(asr);
    asr.add(new AVP_UTF8String(ProtocolConstants.DI_DESTINATION_REALM, dest_realm));
    asr.add(new AVP_UTF8String(ProtocolConstants.DI_DESTINATION_HOST, dest_host));
    asr.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID, auth_app_id));
    Utils.setMandatory_RFC3588(asr);

    //Send it
    final Message asa = ssc.sendRequest(asr);
    if (asa == null) {
      System.out.println("No response");
      return;
    }

    //look at result-code
    final AVP avp_result_code = asa.find(ProtocolConstants.DI_RESULT_CODE);
    if (avp_result_code == null) {
      System.out.println("No result-code in response (?)");
      return;
    }
    final int result_code = new AVP_Unsigned32(avp_result_code).queryValue();
    if (result_code != ProtocolConstants.DIAMETER_RESULT_SUCCESS) {
      System.out.println("Result-code was not success");
      return;
    }

    ssc.stop();
  }
}
