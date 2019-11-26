package org.subethamail.smtp.server;

/**
 * A {@link SessionLifecycleListener} that doesn't perform any real work
 *
 * @author Diego Salvi
 */
public final class NOPSessionLifecycleListener implements SessionLifecycleListener {

    public static final SessionLifecycleListener INSTANCE = new NOPSessionLifecycleListener();

    private NOPSessionLifecycleListener() {
        /* Singleton */
        super();
    }

    @Override
    public SessionStartResult onSessionStart(Session session) {
        return SessionStartResult.success();
    }

    @Override
    public void onSessionEnd(Session session) {
        /* NOP */
    }

}
