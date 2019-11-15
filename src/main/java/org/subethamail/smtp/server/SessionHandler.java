package org.subethamail.smtp.server;

import org.subethamail.smtp.DropConnectionException;

public interface SessionHandler {

    public void acquire(Session session) throws DropConnectionException;

    public void release(Session session);

}
