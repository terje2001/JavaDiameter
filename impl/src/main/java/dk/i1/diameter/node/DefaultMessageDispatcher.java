package dk.i1.diameter.node;

import dk.i1.diameter.Message;

final class DefaultMessageDispatcher implements MessageDispatcher {
  public boolean handle(final Message msg, final ConnectionKey connkey, final Peer peer) {
    return false;
  }
}
