package org.subethamail.smtp.internal.io;

import static org.junit.Assert.assertEquals;
import static org.subethamail.smtp.internal.io.Utf8InputStreamReader.numBytes;

import java.io.ByteArrayInputStream;
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
    public void testNumBytes() {
        assertEquals(1, numBytes('$'));
    }

    private static Utf8InputStreamReader reader(String s) {
        return new Utf8InputStreamReader(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

}
