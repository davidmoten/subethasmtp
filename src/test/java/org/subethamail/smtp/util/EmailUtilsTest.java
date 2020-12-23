package org.subethamail.smtp.util;

import com.github.davidmoten.junit.Asserts;
import org.junit.Assert;
import org.junit.Test;
import org.subethamail.smtp.internal.util.EmailUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EmailUtilsTest {

    @Test
    public void isUtilityClass() {
        Asserts.assertIsUtilityClass(EmailUtils.class);
    }

    @Test
    public void testSpaceAddressIsNotValid() {
        Assert.assertFalse(EmailUtils.isValidEmailAddress(" "));
    }

    @Test
    public void testBlankAddressIsValid() {
        Assert.assertTrue(EmailUtils.isValidEmailAddress(""));
    }

    @Test
    public void testExtract() {
        assertEquals("anyone2@anywhere.com",
            extractAndValidate("TO:<anyone2@anywhere.com>", 3));
    }

    @Test
    public void testExtractWithNoLessThanSymbolAtStartOfEmailAndPrecedingSpace() {
        assertEquals("test@example.com",
            extractAndValidate("FROM: test@example.com", 5));
    }

    @Test
    public void testExtractWithNoLessThanSymbolAtStartOfEmailAndNoPrecedingSpace() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:test@example.com", 5));
    }

    @Test
    public void testExtractWithNoLessThanSymbolAtStartOfEmailAndSIZECommand() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:test@example.com SIZE=1000", 5));
    }

    @Test
    public void testExtractWithEmbeddedPersonalName() {
        // see https://github.com/davidmoten/subethasmtp/issues/17
        assertEquals("Foo Bar <foobar@example.com>",
            extractAndValidate("FROM:<Foo Bar <foobar@example.com>>", 5));
    }

    @Test
    public void testExtractWithAuthVerb() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:<test@example.com> AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndSpaces() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:< test@example.com > AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndNoLessThanSymbolAtStartOfEmail() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:test@example.com AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndNoLessThanSymbolAtStartOfEmailAndPrecedingSpace() {
        assertEquals("test@example.com",
            extractAndValidate("FROM: test@example.com AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndEmbeddedPersonalName() {
        assertEquals("Foo Bar <foobar@example.com>",
            extractAndValidate("FROM:<Foo Bar <foobar@example.com>> AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndEmbeddedPersonalNameAndSpaces() {
        assertEquals("Foo Bar < foobar@example.com >",
            extractAndValidate("FROM:<Foo Bar < foobar@example.com >> AUTH=<>", 5));
    }

    private static String extractAndValidate(String args, int offset) {
        String address = EmailUtils.extractEmailAddress(args, offset);
        assertTrue(address + " isn't a valid address", EmailUtils.isValidEmailAddress(address));
        return address;
    }
}
