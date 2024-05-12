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
        Assert.assertFalse(EmailUtils.isValidEmailAddress(" ", true));
    }

    @Test
    public void testBlankAddressIsValid() {
        Assert.assertTrue(EmailUtils.isValidEmailAddress("", true));
    }
    
    @Test
    public void testSpecialUtf8CharacterIsInvalidInEmailAddress() {
        Assert.assertTrue(EmailUtils.isValidEmailAddress("ñ@abc.com", true));
    }

    @Test
    public void testExtract() {
        assertEquals("anyone2@anywhere.com",
            extractAndValidate("TO:<anyone2@anywhere.com>", 3));
    }

    @Test
    public void testExtractNullSender() {
        assertEquals("",
            extractAndValidate("FROM:<>", 5));
    }

    @Test
    public void testExtractNullSenderWithPrecedingSpace() {
        assertEquals("",
            extractAndValidate("FROM: <>", 5));
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
    public void testExtractWithAuthVerbNullSender() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:<test@example.com> AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuth() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("test@example.com",
            extractAndValidate("FROM:<test@example.com> AUTH=<some@example.com>", 5));
    }

    @Test
    public void testExtractNullSenderWithAuthNullSender() {
        assertEquals("",
            extractAndValidate("FROM:<> AUTH=<>", 5));
    }

    @Test
    public void testExtractNullSenderWithAuth() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("",
            extractAndValidate("FROM:<> AUTH=<some@example.com>", 5));
    }

    @Test
    public void testExtractNullSenderWithAuthNullSenderAndPrecedingSpace() {
        assertEquals("",
            extractAndValidate("FROM: <> AUTH=<>", 5));
    }

    @Test
    public void testExtractNullSenderWithAuthAndPrecedingSpace() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("",
            extractAndValidate("FROM: <> AUTH=<some@example.com>", 5));
    }

    @Test
    public void testExtractWithAuthVerbNullSenderAndSpaces() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:< test@example.com > AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndSpaces() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("test@example.com",
            extractAndValidate("FROM:< test@example.com > AUTH=<some@example.com>", 5));
    }

    @Test
    public void testExtractWithAuthVerbNullSenderAndNoLessThanSymbolAtStartOfEmail() {
        assertEquals("test@example.com",
            extractAndValidate("FROM:test@example.com AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndNoLessThanSymbolAtStartOfEmail() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("test@example.com",
            extractAndValidate("FROM:test@example.com AUTH=<some@example.com>>", 5));
    }

    @Test
    public void testExtractWithAuthVerbNullSenderAndNoLessThanSymbolAtStartOfEmailAndPrecedingSpace() {
        assertEquals("test@example.com",
            extractAndValidate("FROM: test@example.com AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndNoLessThanSymbolAtStartOfEmailAndPrecedingSpace() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("test@example.com",
            extractAndValidate("FROM: test@example.com AUTH=<some@example.com>", 5));
    }

    @Test
    public void testExtractWithAuthVerbNullSenderAndEmbeddedPersonalName() {
        assertEquals("Foo Bar <foobar@example.com>",
            extractAndValidate("FROM:<Foo Bar <foobar@example.com>> AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndEmbeddedPersonalName() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("Foo Bar <foobar@example.com>",
            extractAndValidate("FROM:<Foo Bar <foobar@example.com>> AUTH=<some@example.com>", 5));
    }

    @Test
    public void testExtractWithAuthVerbNullSenderAndEmbeddedPersonalNameAndSpaces() {
        assertEquals("Foo Bar < foobar@example.com >",
            extractAndValidate("FROM:<Foo Bar < foobar@example.com >> AUTH=<>", 5));
    }

    @Test
    public void testExtractWithAuthVerbAndEmbeddedPersonalNameAndSpaces() {
        // AUTH=<some@example.com> out of specs but better safe than sorry
        assertEquals("Foo Bar < foobar@example.com >",
            extractAndValidate("FROM:<Foo Bar < foobar@example.com >> AUTH=<some@example.com>", 5));
    }

    private static String extractAndValidate(String args, int offset) {
        String address = EmailUtils.extractEmailAddress(args, offset);
        assertTrue(address + " isn't a valid address", EmailUtils.isValidEmailAddress(address, true));
        return address;
    }
}
