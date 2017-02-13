import java.util.ArrayList;
import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_UTF8String;
import dk.i1.diameter.AVP_Unsigned32;
import dk.i1.diameter.Message;
import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.Utils;
import dk.i1.diameter.node.Capability;
import dk.i1.diameter.node.ConnectionKey;
import dk.i1.diameter.node.EmptyHostNameException;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeManager;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.NotARequestException;
import dk.i1.diameter.node.NotAnAnswerException;
import dk.i1.diameter.node.NotProxiableException;
import dk.i1.diameter.node.Peer;
import dk.i1.diameter.node.StaleConnectionException;

/**
 * A simple diameter relay.
 * As start-up arguments it takes a bit of configuration including a list of
 * upstream diameter nodes. This example application shows how NodeManager can
 * handle state and keep track of forwarded requests.
 *
 * It does not have any realm-based routing, but instead simply forwards
 * requests to the first available upstream peer, so it is probably not
 * suitable for production use.
 */
final class simple_relay extends NodeManager {
  private final ArrayList<Peer> upstream_peers;

  simple_relay(final NodeSettings node_settings) {
    super(node_settings);
    upstream_peers = new ArrayList<Peer>();
  }

  private void rejectRequest(final Message request, final ConnectionKey connkey, final int why) {
    final Message answer = new Message();
    answer.prepareResponse(request);
    answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, why));
    Utils.setMandatory_RFC3588(answer);
    try {
      answer(answer, connkey);
    } catch (final NotAnAnswerException ex) {
    }
  }

  private boolean isUpstreamPeer(final Peer peer) {
    for (final Peer p : upstream_peers) {
      if (p == peer) {
        return true;
      }
      if (p.equals(peer)) {
        return true;
      }
    }
    return false;
  }

  private static final class ForwardedRequestState {
    ConnectionKey connkey;
    int hop_by_hop_identifier;

    ForwardedRequestState(final ConnectionKey connkey, final int hop_by_hop_identifier) {
      this.connkey = connkey;
      this.hop_by_hop_identifier = hop_by_hop_identifier;
    }
  }

  @Override
  protected void handleRequest(final Message request, final ConnectionKey connkey, final Peer peer) {
    //If destination-host is present we have to honour that.
    final AVP avp_destination_host = request.find(ProtocolConstants.DI_DESTINATION_HOST);
    if (avp_destination_host != null) {
      final String destination_host = new AVP_UTF8String(avp_destination_host).queryValue();
      //If it is ourselves...
      if (destination_host.equals(settings().hostId())) {
        //Since we do not hold any sessions or real state (we are a relay) we can simply reject it.
        rejectRequest(request, connkey, ProtocolConstants.DIAMETER_RESULT_COMMAND_UNSUPPORTED);
      } else {
        //Not ourselves
        ConnectionKey ck = null;
        try {
          ck = node().findConnection(new Peer(destination_host));
        } catch (final EmptyHostNameException ex) {
        }
        if (ck != null) {
          //Forward to peer
          try {
            forwardRequest(request, ck, new ForwardedRequestState(connkey, request.hdr.hop_by_hop_identifier));
          } catch (final StaleConnectionException ex) {
            rejectRequest(request, connkey, ProtocolConstants.DIAMETER_RESULT_UNABLE_TO_DELIVER);
          } catch (final NotARequestException ex) {
            //never happens
          } catch (final NotProxiableException ex) {
            //Imbecile peer
            rejectRequest(request, connkey, ProtocolConstants.DIAMETER_RESULT_UNABLE_TO_DELIVER);
          }
        } else {
          //The destination host could be behind
          //another relay/proxy, but we are a
          //relay therefore too stupid to do
          //intelligent routing.
          rejectRequest(request, connkey, ProtocolConstants.DIAMETER_RESULT_UNABLE_TO_DELIVER);
        }
      }
      return;
    }

    if (isUpstreamPeer(peer)) {
      //If it was a request from an upstream host then we
      //reject it - it is supposed to have a destination-host
      //AVP in the request.
      //(we are a relay and therefore too stupid to do
      //realm-based routing)
      rejectRequest(request, connkey, ProtocolConstants.DIAMETER_RESULT_UNABLE_TO_DELIVER);
    } else {
      //If it is a request from one of the non-upstream peers
      //we simply forward it to one of the upstream peers
      for (final Peer p : upstream_peers) {
        final ConnectionKey ck = node().findConnection(p);
        if (ck == null) {
          continue;
        }
        //Forward to peer
        try {
          forwardRequest(request, ck, new ForwardedRequestState(connkey, request.hdr.hop_by_hop_identifier));
          return;
        } catch (final StaleConnectionException ex) {
          //Unlucky
        } catch (final NotARequestException ex) {
          //never happens
        } catch (final NotProxiableException ex) {
          //Imbecile peer
        }
      }
      //Could not forward to any of the upstream hosts
      rejectRequest(request, connkey, ProtocolConstants.DIAMETER_RESULT_UNABLE_TO_DELIVER);
    }
  }

  @Override
  protected void handleAnswer(final Message answer, final ConnectionKey answer_connkey, final Object state) {
    //Since we never originates requests ourselves, and the NodeManager protects us against unsolicited/unmatched answers it is very simple to handle
    final ForwardedRequestState frs = (ForwardedRequestState) state;
    try {
      answer.hdr.hop_by_hop_identifier = frs.hop_by_hop_identifier;
      forwardAnswer(answer, frs.connkey);
    } catch (final StaleConnectionException ex) {
      //Can happen - there is nothing we can do about it
    } catch (final NotAnAnswerException ex) {
      //never happens
    } catch (final NotProxiableException ex) {
      //???
    }
  }

  public static void main(final String args[]) throws Exception {
    if (args.length < 5) {
      System.out.println("Usage: <vendor-id> <host-id> <realm> <port> [upstream-host...]");
      return;
    }

    final int vendor_id = Integer.parseInt(args[0]);
    final String host_id = args[1];
    final String realm = args[2];
    final int port = Integer.parseInt(args[3]);

    final Capability capability = new Capability();
    capability.addAuthApp(ProtocolConstants.DIAMETER_APPLICATION_RELAY);
    capability.addAcctApp(ProtocolConstants.DIAMETER_APPLICATION_RELAY);

    NodeSettings node_settings;
    try {
      node_settings = new NodeSettings(
          host_id, realm,
          vendor_id,
          capability,
          port,
          "simple_relay (JavaDiameter)", 0x01000000);
    } catch (final InvalidSettingException e) {
      System.out.println(e.toString());
      return;
    }

    final simple_relay sr = new simple_relay(node_settings);

    sr.start();

    //Add the upstream hosts as persistent peers
    for (int i = 4; i < args.length; i++) {
      final Peer peer = new Peer(args[i]);
      sr.upstream_peers.add(peer);
      sr.node().initiateConnection(peer, true);
    }

    //wait for connections to be established
    sr.waitForConnection(150);

    System.out.println("Hit enter to terminate relay");
    System.in.read();

    sr.stop(50); //Stop but allow 50ms graceful shutdown
  }
}
