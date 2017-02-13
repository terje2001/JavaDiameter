import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.node.Capability;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.Peer;
import dk.i1.diameter.session.BaseSession;
import dk.i1.diameter.session.SessionManager;

final class TestSessionTest {
  public static void main(final String args[]) throws Exception {
    if (args.length != 1) {
      System.out.println("Usage: <remote server-name>");
      return;
    }

    final Capability capability = new Capability();
    capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
    capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);

    NodeSettings node_settings;
    try {
      node_settings = new NodeSettings(
          "TestSessionTest.example.net", "example.net",
          99999, //vendor-id
          capability,
          9999, //3868, //port must be non-zero because we have sessions
          "dk.i1.diameter.session.SessionManager test", 0x01000001);
    } catch (final InvalidSettingException e) {
      System.out.println(e.toString());
      return;
    }

    final Peer peers[] = new Peer[] {
      new Peer(args[0])
    };

    final SessionManager session_manager = new SessionManager(node_settings, peers);

    session_manager.start();
    Thread.sleep(500);

    final BaseSession session = new TestSession(ProtocolConstants.DIAMETER_APPLICATION_NASREQ, session_manager);

    session.openSession();
    System.out.println("Session state: " + session.state());

    Thread.sleep(100000);

    System.out.println("Session state: " + session.state());
  }
}
