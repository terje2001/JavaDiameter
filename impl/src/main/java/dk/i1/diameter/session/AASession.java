package dk.i1.diameter.session;

import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_Unsigned32;
import dk.i1.diameter.Message;
import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.Utils;
import lombok.extern.slf4j.Slf4j;

/**
 * A session type that uses the AA messages for authentication/authorization
 * Subclasses should override collectAARInfo() and processAAAInfo().
 * <p>
 * AAASession instances logs with the name "dk.i1.diameter.session.AASession", so
 * you can get detailed logging (including hex-dumps of incoming and outgoing
 * packets) by putting "dk.i1.diameter.session.AASession.level = ALL" into your
 * log.properties file (or equivalent)
 */
@Slf4j
public class AASession extends BaseSession {

  public AASession(final int auth_app_id, final SessionManager session_manager) {
    super(auth_app_id, session_manager);
  }

  /**
   * Handle an answer.
   * This implementation handles AAA messages. All other messages are
   * passed to {@link BaseSession#handleAnswer}
   */
  @Override
  public void handleAnswer(final Message answer, final Object state) {
    switch (answer.hdr.command_code) {
      case ProtocolConstants.DIAMETER_COMMAND_AA:
        handleAAA(answer);
        ;
        break;
      default:
        super.handleAnswer(answer, state);
        break;
    }
  }

  /**
   * Handle an answer.
   * This implementation handles missing AAA messages by calling
   * authFailed(). All other non-answers are passed to {@link BaseSession#handleNonAnswer}
   * passed to {@link BaseSession#handleAnswer}
   */
  @Override
  public void handleNonAnswer(final int command_code, final Object state) {
    switch (command_code) {
      case ProtocolConstants.DIAMETER_COMMAND_AA:
        if (authInProgress()) {
          authFailed(null);
        } else {
          if (log.isInfoEnabled()) {
            log.info("Got a non-answer AA for session '" + sessionId() + "' when no reauth was progress.");
          }
        }
        break;
      default:
        super.handleNonAnswer(command_code, state);
        break;
    }
  }

  /**
   * Handle a AAA message.
   * If the result-code is success then processAAAInfo and authSuccessful are called.
   * If the result-code is multi-round-auth a new AAR is initiated.
   * If the result-code is authorization-reject then the session is closed.
   * If the result-code is anything else then the session is also closed.
   */
  public void handleAAA(final Message msg) {
    log.trace("Handling AAA");
    if (!authInProgress()) {
      return;
    }
    authInProgress(false);
    if (state() == State.discon) {
      return;
    }
    final int result_code = getResultCode(msg);
    switch (result_code) {
      case ProtocolConstants.DIAMETER_RESULT_SUCCESS: {
        final State new_state;
        if (processAAAInfo(msg)) {
          authSuccessful(msg);
        } else {
          closeSession(msg, ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_BAD_ANSWER);
        }
        break;
      }
      case ProtocolConstants.DIAMETER_RESULT_MULTI_ROUND_AUTH:
        sendAAR();
        break;
      case ProtocolConstants.DIAMETER_RESULT_AUTHORIZATION_REJECTED:
        if (log.isInfoEnabled()) {
          log.info("Authorization for session " + sessionId() + " rejected, closing session");
        }
        if (state() == State.pending) {
          closeSession(msg, ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_BAD_ANSWER);
        } else {
          closeSession(msg, ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_AUTH_EXPIRED);
        }
        break;
      default:
        if (log.isInfoEnabled()) {
          log.info("AAR failed, result_code=" + result_code);
        }
        closeSession(msg, ProtocolConstants.DI_TERMINATION_CAUSE_DIAMETER_BAD_ANSWER);
        break;
    }
  }

  @Override
  protected void startAuth() {
    sendAAR();
  }

  @Override
  protected void startReauth() {
    sendAAR();
  }

  private final void sendAAR() {
    if (log.isTraceEnabled()) {
      log.trace("Considering sending AAR for " + sessionId());
    }
    if (authInProgress()) {
      return;
    }
    if (log.isTraceEnabled()) {
      log.trace("Sending AAR for " + sessionId());
    }
    authInProgress(true);
    final Message aar = new Message();
    aar.hdr.setRequest(true);
    aar.hdr.setProxiable(true);
    aar.hdr.application_id = authAppId();
    aar.hdr.command_code = ProtocolConstants.DIAMETER_COMMAND_AA;
    collectAARInfo(aar);
    Utils.setMandatory_RFC3588(aar);
    try {
      sessionManager().sendRequest(aar, this, null);
      //log.trace("AAR sent");
    } catch (final dk.i1.diameter.node.NotARequestException ex) {
      //never happens
    } catch (final dk.i1.diameter.node.NotRoutableException ex) {
      if (log.isInfoEnabled()) {
        log.info("Could not send AAR for session " + sessionId(), ex);
      }
      authFailed(null);
    }
  }

  /**
   * Collect information to send in AAR.
   * This method must be overridden in subclasses to provide essential
   * information such as user-name, password, credenticals, etc.
   * This implementation calls {@link BaseSession#addCommonStuff} and adds the auth-application-id.
   * A subclass probably want to call this method first and then add the session-specific AVPs, eg:
   *
   * <pre>
     void collectAARInfo(Message request) { <i>//method in your session class</i>
         AASession.collectAARInfo(request);
         request.add(new AVP_UTF8String(ProtocolConstants.DI_CALLING_STATION_ID,msisdn));
         ...
     }
   * </pre>
   */
  protected void collectAARInfo(final Message request) {
    addCommonStuff(request);
    request.add(new AVP_Unsigned32(ProtocolConstants.DI_AUTH_APPLICATION_ID, authAppId()));
    //subclasses really need to override this
  }

  /**
   * Process information AAA message.
   * This method extracts authorization-lifetime, auth-grace-period,
   * session-timeout and auth-session-state AVPs and processes them.
   * Subclasses probably want to override this to add additional processing.
   */
  protected boolean processAAAInfo(final Message answer) {
    log.trace("Processing AAA info");
    //subclasses probably want to override this

    //grab a few AVPs
    try {
      AVP avp;
      long auth_lifetime = 0;
      avp = answer.find(ProtocolConstants.DI_AUTHORIZATION_LIFETIME);
      if (avp != null) {
        auth_lifetime = new AVP_Unsigned32(avp).queryValue() * 1000;
      }
      long auth_grace_period = 0;
      avp = answer.find(ProtocolConstants.DI_AUTH_GRACE_PERIOD);
      if (avp != null) {
        auth_grace_period = new AVP_Unsigned32(avp).queryValue() * 1000;
      }
      avp = answer.find(ProtocolConstants.DI_SESSION_TIMEOUT);
      if (avp != null) {
        final int session_timeout = new AVP_Unsigned32(avp).queryValue();
        updateSessionTimeout(session_timeout);
      }
      avp = answer.find(ProtocolConstants.DI_AUTH_SESSION_STATE);
      if (avp != null) {
        final int state_maintained = new AVP_Unsigned32(avp).queryValue();
        stateMaintained(state_maintained == 0);
      }

      final long now = System.currentTimeMillis();
      if (log.isTraceEnabled()) {
        log.trace("Session " + sessionId() + ": now=" + now + "  auth_lifetime=" + auth_lifetime
                + " auth_grace_period=" + auth_grace_period);
      }
      session_auth_timers.updateTimers(now, auth_lifetime, auth_grace_period);
      if (log.isTraceEnabled()) {
        log.trace("getNextReauthTime=" + session_auth_timers.getNextReauthTime() + " getMaxTimeout="
                + session_auth_timers.getMaxTimeout());
      }
    } catch (final dk.i1.diameter.InvalidAVPLengthException ex) {
      return false;
    }
    return true;
  }
}
