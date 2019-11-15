package org.subethamail.smtp.server;

import org.subethamail.smtp.DropConnectionException;

/**
 * A {@link SessionHandler} that doesn't perform any real work
 *
 * @author diego.salvi
 */
public final class DummySessionHandler implements SessionHandler {

    public static final SessionHandler INSTANCE = new DummySessionHandler();

    private DummySessionHandler() {
        /* Singleton */
        super();
    }

    @Override
    public void acquire(Session session) throws DropConnectionException {
        /* NOP */
    }

    @Override
    public void release(Session session) {
        /* NOP */
    }

}
