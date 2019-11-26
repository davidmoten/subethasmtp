package org.subethamail.smtp.server;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A {@link SessionLifecycleListener} to track and limit connection counts by remote addresses.
 *
 * @author Diego Salvi
 */
public class SourceTrackingSessionLifecycleListener implements SessionLifecycleListener {

    /** Session drop response */
    private final SessionStartResult drop;

    private final int maxConnectionsPerSource;
    private final ConcurrentMap<SrcKey, Integer> counts;

    /**
     * Create a new {@link SourceTrackingSessionLifecycleListener} with default reject message:
     * {@code "421 Too many connections, try again later"}.
     *
     * @param maxConnectionsPerSource maximum number of concurrent connection per remote source ip
     */
    public SourceTrackingSessionLifecycleListener(int maxConnectionsPerSource) {
        this(maxConnectionsPerSource, 421, "Too many connections, try again later");
    }

    /**
     * Create a new {@link SourceTrackingSessionLifecycleListener} with custom reject message
     * @param maxConnectionsPerSource maximum number of concurrent connection per remote source ip
     * @param code SMTP code
     * @param message SMTP message
     */
    public SourceTrackingSessionLifecycleListener(int maxConnectionsPerSource, int code, String message) {
        super();

        this.maxConnectionsPerSource = maxConnectionsPerSource;
        this.drop = SessionStartResult.failure(code, message);


        this.counts = new ConcurrentHashMap<>();
    }

    @Override
    public SessionStartResult onSessionStart(Session session) {
        try {
            counts.compute(toKey(session), (k, v) -> {
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
        return SessionStartResult.success();
    }

    @Override
    public void onSessionEnd(Session session) {
        counts.compute(toKey(session), (k, v) -> {
            if (--v == 0) {
                return null;
            } else {
                return v;
            }
        });
    }

    private static SrcKey toKey(Session session) {
        final byte[] src = session.getSocket().getInetAddress().getAddress();
        return new SrcKey(src);
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

    /**
     * Just a wrapper around a byte array to be used as key for a map.
     */
    private static final class SrcKey {

        final byte[] src;
        final int hash;

        public SrcKey(byte[] src) {
            super();
            this.src = src;
            this.hash = internalHashCode();
        }

        private int internalHashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + Arrays.hashCode(src);
            return result;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SrcKey)) {
                return false;
            }
            final SrcKey other = (SrcKey) obj;
            if (!Arrays.equals(src, other.src)) {
                return false;
            }
            return true;
        }

    }

}
