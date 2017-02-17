package dk.i1.diameter;

import java.net.InetAddress;
import lombok.extern.slf4j.Slf4j;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Some end-to-end tests that actually start both a server and client within the same java process, using full tcp/sctp network stack.
 * Not good unit test as such, but gives some basic sanity testing.
 *
 * NB: Host must have a fully qualified domain name with at minumum two '.' (dots) and be fully resolvable.
 */
@Slf4j
public final class EndToEndTest {

  public EndToEndTest() {
  }

  @BeforeClass
  public static void setUpClass() {
  }

  @Test
  public void basicEndToEndCcTcpTest() throws Exception {
    basicEndToEndCcTest(true);
  }

  @Test
  public void basicEndToEndCcSctpTest() throws Exception {
    basicEndToEndCcTest(false);
  }

  private void basicEndToEndCcTest(final boolean useTcp) throws Exception {
    final String localFQDN = InetAddress.getLocalHost().getCanonicalHostName();

    if (log.isDebugEnabled()) {
      log.debug("Using local FQDN as hostId: " + localFQDN);
    }
    final CcServer server = CcServer.create(localFQDN, "example.com", 3868, useTcp);

    try {
      server.start();
      CcClient.doCcRequestResponse("client.domain.com", "example.com", localFQDN, 3868, useTcp);
    } finally {
      server.stop(50);
    }
  }
}
