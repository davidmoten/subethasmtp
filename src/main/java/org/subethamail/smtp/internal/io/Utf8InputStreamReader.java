package org.subethamail.smtp.internal.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

/**
 * No-buffering, no-locking (not thread-safe) InputStream Reader for UTF-8
 * encoded strings. This class exists mainly because
 * {@code java.io.InputStreamReader} does more buffering than strictly necessary
 * (for performance reasons) and this stuffs up passing the underlying
 * InputStream from command to command.
 */
public final class Utf8InputStreamReader extends Reader {

    private static final CharsetDecoder DECODER = StandardCharsets.UTF_8.newDecoder();

    private final InputStream in;
    private final ByteBuffer bb = ByteBuffer.allocate(4);
    private int leftOver = -1;

    public Utf8InputStreamReader(InputStream in) {
        this.in = in;
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) {
            int a = read();
            if (a == -1) {
                if (i == off) {
                    return -1;
                } else {
                    return i - off;
                }
            }
            cbuf[i] = (char) a;
        }
        return len;
    }

    @Override
    public int read() throws IOException {
        if (leftOver != -1) {
            int v = leftOver;
            leftOver = -1;
            return v;
        }
        int b = in.read();
        if (b == -1) {
            return b;
        }
        int numBytes = numBytes(b);
        if (numBytes == 1) {
            return b;
        } else {
            bb.clear();
            bb.put((byte) b);
            for (int i = 0; i < numBytes - 1; i++) {
                byte a = (byte) in.read();
                if (a == -1) {
                    throw new EOFException();
                }
                if (!isContinuation(a)) {
                    throw new IllegalStateException(
                            "wrong continuation bits, bytes after first in a UTF-8 character must start with bits 10");
                }
                bb.put(a);
            }
            bb.flip();
            CharBuffer r = DECODER.decode(bb);
            int v = r.get();
            if (r.limit() > 1) {
                leftOver = r.get();
            }
            return v;
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private static boolean isContinuation(int a) {
        boolean b1 = ((a >> 7) & 1) == 1;
        if (!b1) {
            return false;
        } else {
            boolean b2 = ((a >> 6) & 1) == 1;
            return !b2;
        }
    }

    // VisibleForTesting
    static int numBytes(int a) {
        boolean b1 = bit(a, 1);
        if (!b1) {
            return 1;
        } else {
            boolean b2 = bit(a, 2);
            boolean b3 = bit(a, 3);
            if (!b2) {
                throw new IllegalStateException("leading bits 10 illegal for first byte of UTF-8 character");
            } else if (!b3) {
                return 2;
            } else {
                boolean b4 = bit(a, 4);
                if (!b4) {
                    return 3;
                } else {
                    boolean b5 = bit(a, 5);
                    if (!b5) {
                        return 4;
                    } else {
                        throw new IllegalStateException("leading bits 11111 illegal for first byte of UTF-8 character");
                    }
                }
            }
        }
    }

    private static boolean bit(int a, int index) {
        return ((a >> (8 - index)) & 1) == 1;
    }
}
