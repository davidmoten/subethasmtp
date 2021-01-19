package org.subethamail.wiser;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.subethamail.smtp.internal.Constants;

/**
 * This class wraps a received message and provides a way to generate a JavaMail
 * MimeMessage from the data.
 *
 * @author Jon Stevens
 */
public final class WiserMessage {
    private final byte[] messageData;
    private final Session session;
    private final String envelopeSender;
    private final String envelopeReceiver;

    WiserMessage(Session session, String envelopeSender, String envelopeReceiver, byte[] messageData) {
        this.session = session;
        this.envelopeSender = envelopeSender;
        this.envelopeReceiver = envelopeReceiver;
        this.messageData = messageData;
    }

    /**
     * Generate a JavaMail MimeMessage.
     * 
     * @throws MessagingException
     */
    public MimeMessage getMimeMessage() throws MessagingException {
        return new MimeMessage(session, new ByteArrayInputStream(this.messageData));
    }

    /**
     * Get's the raw message DATA.
     */
    public byte[] getData() {
        return this.messageData;
    }

    /**
     * Get's the RCPT TO:
     */
    public String getEnvelopeReceiver() {
        return this.envelopeReceiver;
    }

    /**
     * Get's the MAIL FROM:
     */
    public String getEnvelopeSender() {
        return this.envelopeSender;
    }

    /**
     * Dumps the rough contents of the message for debugging purposes
     */
    public void dumpMessage(PrintStream out) throws MessagingException {
        out.println("===== Dumping message =====");

        out.println("Envelope sender: " + this.getEnvelopeSender());
        out.println("Envelope recipient: " + this.getEnvelopeReceiver());

        // It should all be convertible with ascii or utf8
        String content = new String(this.getData(), Constants.SMTP_CHARSET);
        out.println(content);

        out.println("===== End message dump =====");
    }

    /**
     * Implementation of toString()
     *
     * @return getData() as a string or an empty string if getData is null
     */
    @Override
    public String toString() {
        if (this.getData() == null)
            return "";

        return new String(this.getData(), Constants.SMTP_CHARSET);
    }
}
