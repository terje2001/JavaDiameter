package dk.i1.diameter.node;

/**
 * Default node validator.
 * This node validator knows all nodes and always allows any capabilities.
 * It does this by not implementing any policy at all, blindly trusting peers
 * that they are who they claim to be and they are allowed to process the
 * applications they announce.
 * 
 * @since 0.9.4
 */
public final class DefaultNodeValidator implements NodeValidator {
  /**
   * "authenticates" peer.
   * Always claims to know any peer.
   */
  public AuthenticationResult authenticateNode(final String node_id, final Object obj) {
    final AuthenticationResult ar = new AuthenticationResult();
    ar.known = true;
    return ar;
  }

  /**
   * "authorizes" the capabilities claimed by a peer.
   * This implementation returns the simple intersection of the peers reported capabilities and our own capabilities.
   * Implemented as <tt>return Capability.calculateIntersection(settings.capabilities(), reported_capabilities);</tt>
   */
  public Capability authorizeNode(final String node_id, final NodeSettings settings,
      final Capability reported_capabilities) {
    final Capability result_capabilities =
        Capability.calculateIntersection(settings.capabilities(), reported_capabilities);
    return result_capabilities;
  }
}
