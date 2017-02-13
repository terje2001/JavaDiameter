package dk.i1.diameter.example;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_UTF8String;
import dk.i1.diameter.AVP_Unsigned32;
import dk.i1.diameter.Message;
import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.session.AASession;
import dk.i1.diameter.session.ACHandler;
import dk.i1.diameter.session.SessionManager;

/**
 * A quite simple session based on AASession, and supports simple accounting.
 * This class is used by some of the other examples.
 */
public class TestSession extends AASession {
  static private Logger logger = Logger.getLogger("TestSession");
  ACHandler achandler;

  public TestSession(final int auth_app_id, final SessionManager session_manager) {
    super(auth_app_id, session_manager);
    achandler = new ACHandler(this);
    achandler.acct_application_id = ProtocolConstants.DIAMETER_APPLICATION_NASREQ;
  }

  @Override
  public void handleAnswer(final Message answer, final Object state) {
    logger.log(Level.FINE, "processing answer");
    switch (answer.hdr.command_code) {
      case ProtocolConstants.DIAMETER_COMMAND_ACCOUNTING:
        achandler.handleACA(answer);
        break;
      default:
        super.handleAnswer(answer, state);
        break;
    }
  }

  @Override
  public void handleNonAnswer(final int command_code, final Object state) {
    logger.log(Level.FINE, "processing non-answer");
    switch (command_code) {
      case ProtocolConstants.DIAMETER_COMMAND_ACCOUNTING:
        achandler.handleACA(null);
        break;
      default:
        super.handleNonAnswer(command_code, state);
        break;
    }
  }

  @Override
  protected void collectAARInfo(final Message request) {
    super.collectAARInfo(request);
    request.add(new AVP_UTF8String(ProtocolConstants.DI_USER_NAME, "user@example.net"));
  }

  @Override
  protected boolean processAAAInfo(final Message answer) {
    try {
      final Iterator<AVP> it = answer.iterator(ProtocolConstants.DI_ACCT_INTERIM_INTERVAL);
      if (it.hasNext()) {
        final int interim_interval = new AVP_Unsigned32(it.next()).queryValue();
        if (interim_interval != 0) {
          achandler.subSession(0).interim_interval = interim_interval * 1000;
        }
      }
    } catch (final dk.i1.diameter.InvalidAVPLengthException e) {
      return false;
    }
    return super.processAAAInfo(answer);
  }

  @Override
  public long calcNextTimeout() {
    long t = super.calcNextTimeout();
    if (state() == State.open) {
      t = Math.min(t, achandler.calcNextTimeout());
    }
    return t;
  }

  @Override
  public void handleTimeout() {
    //update acct_session_time
    if (state() == State.open) {
      achandler.subSession(0).acct_session_time = System.currentTimeMillis() - firstAuthTime();
    }
    //Then do the timeout handling
    super.handleTimeout();
    achandler.handleTimeout();
  }

  @Override
  public void newStatePre(final State prev_state, final State new_state, final Message msg, final int cause) {
    logger.log(Level.FINE, "prev=" + prev_state + " new=" + new_state);
    if (prev_state != State.discon && new_state == State.discon) {
      achandler.stopSession();
    }
  }

  @Override
  public void newStatePost(final State prev_state, final State new_state, final Message msg, final int cause) {
    logger.log(Level.FINE, "prev=" + prev_state + " new=" + new_state);
    if (prev_state != State.open && new_state == State.open) {
      achandler.startSession();
    }
  }
}
