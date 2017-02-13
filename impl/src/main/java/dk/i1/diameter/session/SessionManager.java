package dk.i1.diameter.session;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import dk.i1.diameter.AVP;
import dk.i1.diameter.AVP_Grouped;
import dk.i1.diameter.AVP_UTF8String;
import dk.i1.diameter.AVP_Unsigned32;
import dk.i1.diameter.Message;
import dk.i1.diameter.ProtocolConstants;
import dk.i1.diameter.Utils;
import dk.i1.diameter.node.ConnectionKey;
import dk.i1.diameter.node.InvalidSettingException;
import dk.i1.diameter.node.NodeManager;
import dk.i1.diameter.node.NodeSettings;
import dk.i1.diameter.node.NotARequestException;
import dk.i1.diameter.node.NotRoutableException;
import dk.i1.diameter.node.Peer;
import dk.i1.diameter.node.UnsupportedTransportProtocolException;
import lombok.extern.slf4j.Slf4j;

/**
 * A go-between sessions and NodeManager.
 * The SessionManager keeps track of outstanding requests and dispatches
 * answers to the sessions. It also keeps track of the timeouts in the
 * sessions.
 * <p>
 * SessionManager instances logs with the name "dk.i1.diameter.session", so
 * you can get detailed logging (including hex-dumps of incoming and outgoing
 * packets) by putting "dk.i1.diameter.session.level = ALL" into your
 * log.properties file (or equivalent)
 */
@Slf4j
public final class SessionManager extends NodeManager {

  private static class SessionAndTimeout {

    public Session session;
    public long timeout;
    public boolean deleted;

    public SessionAndTimeout(final Session session) {
      this.session = session;
      this.timeout = session.calcNextTimeout();
      this.deleted = false;
    }
  }

  private final Map<String, SessionAndTimeout> map_session;
  private final Peer peers[];
  private Thread timer_thread;
  private long earliest_timeout;
  private boolean stop;

  /**
   * Constructor for SessionManager.
   *
   * @param settings The node settings
   * @param peers The default set of peers. If a subclass overrides the
   *        peers() methods then this parameter can be null.
   */
  public SessionManager(final NodeSettings settings, final Peer peers[]) throws InvalidSettingException {
    super(settings);
    if (settings.port() == 0) {
      throw new InvalidSettingException("If you have sessions then you must allow inbound connections");
    }
    map_session = new HashMap<String, SessionAndTimeout>();
    this.peers = peers;
    earliest_timeout = Long.MAX_VALUE;
    stop = false;
  }

  /**
   * Start the session manager.
   * The SessionManager must be started before it can be used by sessions.
   */
  @Override
  public void start() throws java.io.IOException, UnsupportedTransportProtocolException {
    log.trace("Starting session manager");
    super.start();
    timer_thread = new TimerThread();
    timer_thread.setDaemon(true);
    timer_thread.start();
    for (final Peer p : peers) {
      super.node().initiateConnection(p, true);
    }
  }

  /**
   * Stop the SessionManager.
   *
   * @param grace_time Maximum time (milliseconds) to wait for connections to close gracefully.
   * @since grace_time parameter introduced in 0.9.3
   */
  @Override
  public void stop(final long grace_time) {
    log.trace("Stopping session manager");
    super.stop(grace_time);
    synchronized (map_session) {
      stop = true;
      map_session.notify();
    }
    try {
      timer_thread.join();
    } catch (final InterruptedException e) {
    }
    log.trace("Session manager stopped");
  }

  /**
   * Handle incoming request.
   * Examines the Session-Id AVP and dispatches the request to the session.
   */
  @Override
  protected void handleRequest(final Message request, final ConnectionKey connkey, final Peer peer) {
    if (log.isTraceEnabled()) {
      log.trace("Handling request, command_code=" + request.hdr.command_code);
    }
    //todo: verify that destination-host is us
    final Message answer = new Message();
    answer.prepareResponse(request);

    final String session_id = extractSessionId(request);
    if (session_id == null) {
      log.trace("Cannot handle request - no Session-Id AVP in request");
      answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_MISSING_AVP));
      node().addOurHostAndRealm(answer);
      answer.add(new AVP_Grouped(ProtocolConstants.DI_FAILED_AVP,
              new AVP[]{new AVP_UTF8String(ProtocolConstants.DI_SESSION_ID, "")}));
      Utils.copyProxyInfo(request, answer);
      Utils.setMandatory_RFC3588(answer);
      try {
        answer(answer, connkey);
      } catch (final dk.i1.diameter.node.NotAnAnswerException ex) {
      }
      return;
    }
    final Session s = findSession(session_id);
    if (s == null) {
      if (log.isTraceEnabled()) {
        log.trace("Cannot handle request - Session-Id '" + session_id + " does not denote a known session");
      }
      answer.add(
              new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, ProtocolConstants.DIAMETER_RESULT_UNKNOWN_SESSION_ID));
      node().addOurHostAndRealm(answer);
      Utils.copyProxyInfo(request, answer);
      Utils.setMandatory_RFC3588(answer);
      try {
        answer(answer, connkey);
      } catch (final dk.i1.diameter.node.NotAnAnswerException ex) {
      }
      return;
    }
    final int result_code = s.handleRequest(request);
    answer.add(new AVP_Unsigned32(ProtocolConstants.DI_RESULT_CODE, result_code));
    node().addOurHostAndRealm(answer);
    Utils.copyProxyInfo(request, answer);
    Utils.setMandatory_RFC3588(answer);
    try {
      answer(answer, connkey);
    } catch (final dk.i1.diameter.node.NotAnAnswerException ex) {
    }
  }

  private static class RequestState {

    public int command_code;
    public Object state;
    public Session session;
  }

  /**
   * Handle an answer to an outstanding request.
   * Dispatches the answer to the corresponding session by calling
   * either Session.handleAnswer() or Session.handleNonAnswer()
   */
  @Override
  protected void handleAnswer(final Message answer, final ConnectionKey answer_connkey, final Object state) {
    if (log.isTraceEnabled()) {
      if (answer != null) {
        log.trace("Handling answer, command_code=" + answer.hdr.command_code);
      } else {
        log.trace("Handling non-answer");
      }
    }
    final Session s;
    final String session_id = extractSessionId(answer);
    if (log.isTraceEnabled()) {
      log.trace("session-id=" + session_id);
    }
    if (session_id != null) {
      s = findSession(session_id);
    } else {
      s = ((RequestState) state).session;
    }
    if (s == null) {
      if (log.isTraceEnabled()) {
        log.trace("Session '" + session_id + "' not found");
      }
      return;
    }
    log.trace("Found session, dispatching (non-)answer to it");

    if (answer != null) {
      s.handleAnswer(answer, ((RequestState) state).state);
    } else {
      s.handleNonAnswer(((RequestState) state).command_code, ((RequestState) state).state);
    }
  }

  /**
   * Send a request for a session.
   * Sessions must use this method and not NodeManager.sendRequest(), as
   * the SessionManager must keep track of outstanding requests.
   * Note that the session's handleAnswer() method may get called before
   * this method returns.
   *
   * @param request The request to send-
   * @param session The session the request is sent on behalf of.
   * @param state A state object that will be given in the {@link Session#handleAnswer} or
   *        {@link Session#handleNonAnswer} call.
   */
  public void sendRequest(final Message request, final Session session, final Object state)
          throws NotRoutableException, NotARequestException {
    if (log.isTraceEnabled()) {
      log.trace("Sending request (command_code=" + request.hdr.command_code + ") for session " + session.sessionId());
    }
    final RequestState rs = new RequestState();
    rs.command_code = request.hdr.command_code;
    rs.state = state;
    rs.session = session;
    sendRequest(request, peers(request), rs);
  }

  /**
   * Retrieve the default set of peers.
   *
   * @return The set of peers specified in the constructor.
   */
  public Peer[] peers() {
    return peers;
  }

  /**
   * Retrieve a set of peers suitable for the specified request.
   * A subclass can override this method to implement more
   * intelligent peer selection.
   *
   * @param request The request that will be sent to one of the returned peers.
   * @return a set of suitable peers.
   */
  public Peer[] peers(final Message request) {
    return peers;
  }

  /**
   * Register a session for management.
   * The BaseSession class calls this method when appropriate
   *
   * @param s The Session to be registered.
   */
  public void register(final Session s) {
    final SessionAndTimeout sat = new SessionAndTimeout(s);
    synchronized (map_session) {
      map_session.put(s.sessionId(), sat);
      if (sat.timeout < earliest_timeout) {
        map_session.notify(); //wake it so it can re-calculate timeouts
      }
    }
  }

  /**
   * Unregister a session for management.
   * The BaseSession class calls this method when appropriate.
   *
   * @param s The Session to be registered.
   */
  public void unregister(final Session s) {
    if (log.isTraceEnabled()) {
      log.trace("Unregistering session " + s.sessionId());
    }
    synchronized (map_session) {
      final SessionAndTimeout sat = map_session.get(s.sessionId());
      if (sat != null) {
        sat.deleted = true;
        if (earliest_timeout == Long.MAX_VALUE) {
          map_session.notify(); //wake it so it can remove it
        }
        return;
      }
    }
    log.warn("Could not find session " + s.sessionId());
  }

  /**
   * Update the timeouts for a session.
   * When some timeout changes in a session then the session must call
   * this method to let the SessionManager re-calculate the timeout and
   * update internal state accordingly.
   */
  public void updateTimeouts(final Session s) {
    synchronized (map_session) {
      final SessionAndTimeout sat = map_session.get(s.sessionId());
      if (sat == null) {
        return; //actually an error, but a harmless one
      }
      sat.timeout = s.calcNextTimeout();
      if (sat.timeout < earliest_timeout) {
        map_session.notify(); //wake it so it can re-calculate timeouts
      }
    }
  }

  private Session findSession(final String session_id) {
    synchronized (map_session) {
      final SessionAndTimeout sat = map_session.get(session_id);
      return (sat != null && !sat.deleted) ? sat.session : null;
    }
  }

  private String extractSessionId(final Message msg) {
    if (msg == null) {
      return null;
    }
    final Iterator<AVP> it = msg.iterator(ProtocolConstants.DI_SESSION_ID);
    if (!it.hasNext()) {
      return null;
    }
    return new AVP_UTF8String(it.next()).queryValue();
  }

  private class TimerThread extends Thread {

    public TimerThread() {
      super("SessionManager timer thread");
    }

    @Override
    public void run() {
      synchronized (map_session) {
        while (!stop) {
          long now = System.currentTimeMillis();
          earliest_timeout = Long.MAX_VALUE;
          for (final Iterator<Map.Entry<String, SessionAndTimeout>> it = map_session.entrySet().iterator(); it
                  .hasNext();) {
            final Map.Entry<String, SessionAndTimeout> e = it.next();
            if (e.getValue().deleted) {
              it.remove();
              continue;
            }
            final Session session = e.getValue().session;
            if (e.getValue().timeout < now) {
              session.handleTimeout();
              e.getValue().timeout = session.calcNextTimeout();
            }
            earliest_timeout = Math.min(earliest_timeout, e.getValue().timeout);
          }

          now = System.currentTimeMillis();
          try {
            if (earliest_timeout > now) {
              if (earliest_timeout == Long.MAX_VALUE) {
                map_session.wait();
              } else {
                map_session.wait(earliest_timeout - now);
              }
            }
          } catch (final InterruptedException e) {
          }
        }
      }
    }
  }
}
