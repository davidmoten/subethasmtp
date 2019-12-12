package org.subethamail.smtp.server;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link SessionHandler} to track and limit connection counts by remote addresses.
 *
 * @author Diego Salvi
 */
public class SourceTrackingSessionHandler implements SessionHandler {

    /** Session drop response */
    private final SessionAcceptance drop;

    private final int maxConnectionsPerSource;
    private final ConcurrentMap<InetAddress, Integer> counts;

    /**
     * Create a new {@link SourceTrackingSessionHandler} with default reject message:
     * {@code "421 Too many connections, try again later"}.
     *
     * @param maxConnectionsPerSource maximum number of concurrent connection per remote source ip
     */
    public SourceTrackingSessionHandler(int maxConnectionsPerSource) {
        this(maxConnectionsPerSource, 421, "Too many connections, try again later");
    }

    /**
     * Create a new {@link SourceTrackingSessionHandler} with custom reject message
     * @param maxConnectionsPerSource maximum number of concurrent connection per remote source ip
     * @param code SMTP code
     * @param message SMTP message
     */
    public SourceTrackingSessionHandler(int maxConnectionsPerSource, int code, String message) {
        super();

        this.maxConnectionsPerSource = maxConnectionsPerSource;
        this.drop = SessionAcceptance.failure(code, message);


        this.counts = new ConcurrentHashMap<>();
    }

    @Override
    public SessionAcceptance accept(Session session) {
        try {
            counts.compute(toInetAddress(session), (k, v) -> {
                if (v == null) {
                    return 1;
                } else {

                    if (v == maxConnectionsPerSource) {
                        throw LimitReachedException.INSTANCE;
                    } else {
                        return ++v;
                    }
                }
            });
        } catch (LimitReachedException limit) {
            return drop;
        }
        return SessionAcceptance.success();
    }

    @Override
    public void onSessionEnd(Session session) {
        counts.compute(toInetAddress(session), (k, v) -> {
            if (--v == 0) {
                return null;
            } else {
                return v;
            }
        });
    }

    private static InetAddress toInetAddress(Session session) {
        return session.getSocket().getInetAddress();
    }

    /**
     * A lightweight exception to avoid to create non useful stacktraces, this exception is use only internally
     * and never leaked out this class.
     */
    @SuppressWarnings("serial")
    private static final class LimitReachedException extends RuntimeException {
        public static final LimitReachedException INSTANCE = new LimitReachedException();

        public LimitReachedException() {
            /* Disables stacktraces and suppressions */
            super("Limit reached", null, false, false);
        }
    }

}
