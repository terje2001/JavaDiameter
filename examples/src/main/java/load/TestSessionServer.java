import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_Unsigned32;
import dk.i1.diameter.Message;
import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.Utils;
import dk.i1.diameter.node.Capability;
import dk.i1.diameter.node.ConnectionKey;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeManager;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.Peer;

final class TestSessionServer extends NodeManager {
  TestSessionServer(final NodeSettings node_settings) {
    super(node_settings);
  }

  public static void main(final String args[]) throws Exception {
    if (args.length != 1) {
      System.out.println("Usage: <host-id>");
      return;
    }
    final Capability capability = new Capability();
    capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
    capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);

    NodeSettings node_settings;
    try {
      node_settings = new NodeSettings(
          args[0], "example.net",
          99999, //vendor-id
          capability,
          3868,
          "TestSessionServer", 0x01000000);
    } catch (final InvalidSettingException e) {
      System.out.println(e.toString());
      return;
    }

    final TestSessionServer tss = new TestSessionServer(node_settings);
    tss.start();

    System.out.println("Hit enter to terminate server");
    System.in.read();

    tss.stop();
  }

  @Override
  protected void handleRequest(final Message request, final ConnectionKey connkey, final Peer peer) {
    //this is not the way to do it, but fine for a lean-and-mean test server
    final Message answer = new Message();
    answer.prepareResponse(request);
    final AVP avp_session_id = request.find(ProtocolConstants.DI_SESSION_ID);
    if (avp_session_id != null) {
      answer.add(avp_session_id);
    }
    answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_SUCCESS));
    final AVP avp_auth_app_id = request.find(ProtocolConstants.DI_AUTH_APPLICATION_ID);
    if (avp_auth_app_id != null) {
      answer.add(avp_auth_app_id);
    }

    switch (request.hdr.command_code) {
      case ProtocolConstants.DIAMETER_COMMAND_AA:
        //answer.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTHORIZATION_LIFETIME,60));
        break;
    }

    Utils.setMandatory_RFC3588(answer);

    try {
      answer(answer, connkey);
    } catch (final dk.i1.diameter.node.NotAnAnswerException ex) {
    }
  }
}
