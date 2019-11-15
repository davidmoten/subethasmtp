package org.subethamail.smtp.server;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.subethamail.smtp.DropConnectionException;

/**
 * A {@link SessionHandler} to track and limit connection counts by remote addresses.
 *
 * @author diego.salvi
 */
public class SourceTrackingSessionHandler implements SessionHandler {

    /**
     * Template exception thrown by this facility. Generated only once to avoid not useful stacktrace
     * generations.
     */
    private static final DropConnectionException DROP =
            new DropConnectionException(421, "Too many connections, try again later");

    private final int maxConnectionsPerSource;
    private final ConcurrentMap<SrcKey, Integer> counts;

    public SourceTrackingSessionHandler(int maxConnectionsPerSource) {
        super();

        this.maxConnectionsPerSource = maxConnectionsPerSource;
        this.counts = new ConcurrentHashMap<>();
    }

    @Override
    public void acquire(Session session) throws DropConnectionException {
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
            throw DROP;
        }
    }

    @Override
    public void release(Session session) {
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
     * A lightway exception to avoid to create non useful stacktraces, this exception is use only internally
     * and never leaked out this class.
     */
    @SuppressWarnings("serial")
    private static final class LimitReachedException extends RuntimeException {
        public static final LimitReachedException INSTANCE = new LimitReachedException();

        public LimitReachedException() {
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
