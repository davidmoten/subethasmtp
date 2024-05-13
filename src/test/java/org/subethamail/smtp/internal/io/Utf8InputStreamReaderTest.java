package org.subethamail.smtp.internal.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.subethamail.smtp.internal.io.Utf8InputStreamReader.numBytes;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class Utf8InputStreamReaderTest {

    @Test
    public void test() throws IOException {
        final char[] chars = Character.toChars(0x1F701);
        assertEquals(2, chars.length);
        final String str = new String(chars);
        String s = "$£Иह€한薠" + str;
        try (Reader r = reader(s)) {
            assertEquals('$', (char) r.read());
            assertEquals('£', (char) r.read());
            assertEquals('И', (char) r.read());
            assertEquals('ह', (char) r.read());
            assertEquals('€', (char) r.read());
            assertEquals('한', (char) r.read());
            assertEquals('薠', (char) r.read());
            char[] chrs = new char[2];
            assertEquals(2, r.read(chrs));
            assertEquals(55357, chrs[0]);
            assertEquals(57089, chrs[1]);
            assertEquals(-1, r.read());
        }
    }

    @Test
    public void testReadIntoArray() throws IOException {
        final char[] chars = Character.toChars(0x1F701);
        final String str = new String(chars);
        String s = "$£Иह€한薠" + str;
        try (Reader r = reader(s)) {
            char[] chrs = new char[1000];
            int n = r.read(chrs, 0, 2);
            n += r.read(chrs, 2, 1000);
            assertEquals(9, n);
            assertEquals(-1, r.read(chrs));
        }
    }

    @Test
    public void testEarlyEof() throws IOException {
        byte[] bytes = "£".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[] { bytes[0] };
        try (Reader r = new Utf8InputStreamReader(new ByteArrayInputStream(b))) {
            assertThrows(EOFException.class, () -> r.read());
        }
    }

    @Test
    public void testNotContinuation() throws IOException {
        byte[] bytes = "£".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[] { bytes[0], bytes[0] };
        try (Reader r = new Utf8InputStreamReader(new ByteArrayInputStream(b))) {
            assertThrows(IllegalStateException.class, () -> r.read());
        }
    }

    @Test
    public void testNotContinuation2() throws IOException {
        byte[] bytes = "£".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[] { bytes[0], '$' };
        try (Reader r = new Utf8InputStreamReader(new ByteArrayInputStream(b))) {
            assertThrows(IllegalStateException.class, () -> r.read());
        }
    }

    @Test
    public void testContinuationByteCannotBeFirstByte() throws IOException {
        byte[] bytes = "£".getBytes(StandardCharsets.UTF_8);
        byte[] b = new byte[] { bytes[1] };
        try (Reader r = new Utf8InputStreamReader(new ByteArrayInputStream(b))) {
            assertThrows(IllegalStateException.class, () -> r.read());
        }
    }

    @Test
    public void testUtf8ByteHasTooManyLeadingOnes() throws IOException {
        byte[] b = new byte[] { (byte) 248 };
        try (Reader r = new Utf8InputStreamReader(new ByteArrayInputStream(b))) {
            assertThrows(IllegalStateException.class, () -> r.read());
        }
    }

    @Test
    public void testNumBytes() {
        assertEquals(1, numBytes('$'));
    }

    private static Utf8InputStreamReader reader(String s) {
        return new Utf8InputStreamReader(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

}
