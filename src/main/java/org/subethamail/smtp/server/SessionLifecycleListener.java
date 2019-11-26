package org.subethamail.smtp.server;

import com.github.davidmoten.guavamini.Preconditions;

/**
 * Listener on session lifecycle events.
 *
 * @author Diego Salvi
 */
public interface SessionLifecycleListener {

    /**
     * This method is invoked on a session creation, before sending the SMTP greeting and can react rejecting
     * the session.
     * <p>
     * Rejected session will be closed and no method {@link #onSessionEnd(Session)} will be invoked.
     * </p>
     *
     * @param session newly created session
     * @return starting session result event, can allow or reject the newly created session
     */
    SessionStartResult onSessionStart(Session session) ;

    /**
     * This method is invoked on session close.
     *
     * @param session closing session
     */
    void onSessionEnd(Session session);

    /**
     * Result object for {@link SessionLifecycleListener#onSessionStart(Session)}
     *
     * @author Diego Salvi
     */
    public static final class SessionStartResult {

        /** Singleton success result */
        private static final SessionStartResult SUCCESS = new SessionStartResult(true, -1, null);

        /**
         * Returns a success {@link SessionLifecycleListener#onSessionStart(Session)} result.
         *
         * @return session start success
         */
        public static SessionStartResult success() {
            return SUCCESS;
        }

        /**
         * Returns a failed {@link SessionLifecycleListener#onSessionStart(Session)} result.
         *
         * @param code SMTP failure result code
         * @param message SMTP failure result message
         * @return session start failure
         */
        public static SessionStartResult failure(int code, String message) {
            /* Check that code is a failure response! */
            Preconditions.checkArgument(code > 199 && code < 600, "Invalid SMTP response code " + code);
            return new SessionStartResult(false, code, message);
        }

        private final boolean accepted;
        private final int errorCode;
        private final String errorMessage;

        private SessionStartResult(boolean accepted, int errorCode, String errorMessage) {
            super();
            this.accepted = accepted;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public boolean isAccepted() {
            return accepted;
        }

        public int getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

}
