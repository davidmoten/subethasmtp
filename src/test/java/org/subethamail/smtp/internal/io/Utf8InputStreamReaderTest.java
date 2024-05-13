package org.subethamail.smtp.internal.io;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class Utf8InputStreamReaderTest {

    @Test
    public void test() throws IOException {
        String s = "$£Иह€한薠";
        try (Reader r = reader(s)) {
            assertEquals('$', (char) r.read());
            assertEquals('£', (char) r.read());
            assertEquals('И', (char) r.read());
            assertEquals('ह', (char) r.read());
            assertEquals('€', (char) r.read());
            assertEquals('한', (char) r.read());
            assertEquals('薠', (char) r.read());
            assertEquals(-1, r.read());
            System.out.println((char) 0x10348);
        }
    }

    private static Utf8InputStreamReader reader(String s) {
        return new Utf8InputStreamReader(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

}
