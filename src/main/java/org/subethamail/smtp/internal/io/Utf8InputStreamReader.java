package org.subethamail.smtp.internal.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

/**
 * No-buffering InputStream Reader for UTF-8 encoded strings.
 */
public final class Utf8InputStreamReader extends Reader {

    private static final CharsetDecoder DECODER = StandardCharsets.UTF_8.newDecoder();

    private final InputStream in;
    private final ByteBuffer bb = ByteBuffer.allocate(4);

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
                    throw new IllegalStateException("wrong continuation bits");
                }
                bb.put(a);
            }
            bb.flip();
            return DECODER.decode(bb).get();
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

    private static int numBytes(int a) {
        boolean b1 = ((a >> 7) & 1) == 1;
        if (!b1) {
            return 1;
        } else {
            boolean b2 = ((a >> 6) & 1) == 1;
            boolean b3 = ((a >> 5) & 1) == 1;
            if (!b2) {
                throw new IllegalStateException();
            } else if (!b3) {
                return 2;
            } else {
                boolean b4 = ((a >> 4) & 1) == 1;
                if (!b4) {
                    return 3;
                } else {
                    boolean b5 = ((a >> 3) & 1) == 1;
                    if (!b5) {
                        return 4;
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

}
