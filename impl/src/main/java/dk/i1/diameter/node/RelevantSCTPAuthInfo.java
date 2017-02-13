package dk.i1.diameter.node;

import dk.i1.sctp.AssociationId;
import dk.i1.sctp.SCTPSocket;

/**
 * Peer authentication information (SCTP).
 * Instances of this class is used for passing information to {@link NodeValidator}.
 * 
 * @since 0.9.5
 */
public final class RelevantSCTPAuthInfo {
  public SCTPSocket sctp_socket;
  public AssociationId assoc_id;

  RelevantSCTPAuthInfo(final SCTPSocket sctp_socket, final AssociationId assoc_id) {
    this.sctp_socket = sctp_socket;
    this.assoc_id = assoc_id;
  }
}
