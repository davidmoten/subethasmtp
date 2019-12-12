package org.subethamail.smtp.server;

/**
 * A {@link SessionHandler} that doesn't perform any real work
 *
 * @author Diego Salvi
 */
public final class AcceptAllSessionHandler implements SessionHandler {

    public static final SessionHandler INSTANCE = new AcceptAllSessionHandler();

    private AcceptAllSessionHandler() {
        /* Singleton */
        super();
    }

    @Override
    public SessionAcceptance accept(Session session) {
        return SessionAcceptance.success();
    }

    @Override
    public void onSessionEnd(Session session) {
        /* NOP */
    }

}
