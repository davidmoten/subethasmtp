package org.subethamail.smtp.server;

import com.github.davidmoten.guavamini.Preconditions;

/**
 * Handler of session lifecycle events.
 *
 * @author Diego Salvi
 */
public interface SessionHandler {

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
    SessionAcceptance accept(Session session) ;

    /**
     * This method is invoked on session close.
     *
     * @param session closing session
     */
    void onSessionEnd(Session session);

    /**
     * Result object for {@link SessionHandler#accept(Session)}
     *
     * @author Diego Salvi
     */
    public static final class SessionAcceptance {

        /** Singleton success result */
        private static final SessionAcceptance SUCCESS = new SessionAcceptance(true, -1, null);

        /**
         * Returns a success {@link SessionHandler#accept(Session)} result.
         *
         * @return session start success
         */
        public static SessionAcceptance success() {
            return SUCCESS;
        }

        /**
         * Returns a failed {@link SessionHandler#accept(Session)} result.
         *
         * @param code SMTP failure result code
         * @param message SMTP failure result message
         * @return session start failure
         */
        public static SessionAcceptance failure(int code, String message) {
            /* Check that code is a failure response! */
            Preconditions.checkArgument(code > 199 && code < 600, "Invalid SMTP response code " + code);
            return new SessionAcceptance(false, code, message);
        }

        private final boolean accepted;
        private final int errorCode;
        private final String errorMessage;

        private SessionAcceptance(boolean accepted, int errorCode, String errorMessage) {
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
