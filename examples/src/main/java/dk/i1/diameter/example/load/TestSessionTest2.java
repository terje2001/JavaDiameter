package dk.i1.diameter.example.load;

import dk.i1.diameter.Message;
import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.example.TestSession;
import dk.i1.diameter.node.Capability;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.Peer;
import dk.i1.diameter.session.SessionManager;

/**
 * Generate load by running through a lot of sessions.
 * It is meant to be used with the TestSessionServer.
 */
final class TestSessionTest2 {
  static int sessions_actually_opened = 0;

  public static void main(final String args[]) throws Exception {
    if (args.length != 4) {
      System.out.println("Usage: <peer> <sessions> <rate> <duration>");
      return;
    }

    final String peer = args[0];
    final int sessions = Integer.parseInt(args[1]);
    final double rate = Double.parseDouble(args[2]);
    final int session_duration = Integer.parseInt(args[3]);

    final Capability capability = new Capability();
    capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);
    capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_NASREQ);

    NodeSettings node_settings;
    try {
      node_settings = new NodeSettings(
          "TestSessionTest2.example.net", "example.net",
          99999, //vendor-id
          capability,
          9999,
          "TestSessionTest2", 0x01000000);
    } catch (final InvalidSettingException e) {
      System.out.println(e.toString());
      return;
    }

    final Peer peers[] = new Peer[] {
      new Peer(peer)
      //new Peer(peer,3868,Peer.TransportProtocol.sctp)
    };

    final SessionManager session_manager = new SessionManager(node_settings, peers);

    session_manager.start();
    Thread.sleep(2000); //allow connections to be established.

    for (int i = 0; i != sessions; i++) {
      final TestSession ts = new TestSession(ProtocolConstants.DIAMETER_APPLICATION_NASREQ, session_manager) {
        public void newStatePost(final State prev_state, final State new_state, final Message msg, final int cause) {
          if (new_state == State.open) {
            updateSessionTimeout(session_duration);
            sessions_actually_opened++;
          }
          super.newStatePost(prev_state, new_state, msg, cause);
        }
      };
      ts.openSession();
      Thread.sleep((long) (1000 / rate));
    }

    Thread.sleep(session_duration * 1000 + 50);

    System.out.println("sessions_actually_opened=" + sessions_actually_opened);
  }
}
