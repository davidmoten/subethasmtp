package org.subethamail.smtp.internal.io;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public class Utf8InputStreamReaderTest {

    @Test
    public void test() throws IOException {
        final char[] chars = Character.toChars(0x1F701);
        final String str = new String(chars);
        final byte[] asBytes = str.getBytes(StandardCharsets.UTF_8);
        System.out.println(chars.length);
        System.out.println(str);
        System.out.println(Arrays.toString(asBytes));
        String s = "$£Иह€한薠" + (char) 0x1F701;
        try (Reader r = reader(s)) {
            assertEquals('$', (char) r.read());
            assertEquals('£', (char) r.read());
            assertEquals('И', (char) r.read());
            assertEquals('ह', (char) r.read());
            assertEquals('€', (char) r.read());
            assertEquals('한', (char) r.read());
            assertEquals('薠', (char) r.read());
            assertEquals(s.charAt(s.length() - 1), (char) r.read());
            assertEquals(-1, r.read());
        }
    }

    private static Utf8InputStreamReader reader(String s) {
        return new Utf8InputStreamReader(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

}
