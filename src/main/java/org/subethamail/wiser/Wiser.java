/*
 * $Id$
 * $URL$
 */

package org.subethamail.wiser;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.helper.SimpleMessageListener;
import org.subethamail.smtp.server.SMTPServer;
import org.subethamail.smtp.server.SMTPServer.Builder;

/**
 * Wiser is a tool for unit testing applications that send mail. Your unit tests
 * can start Wiser, run tests which generate emails, then examine the emails
 * that Wiser received and verify their integrity.
 *
 * Wiser is not intended to be a "real" mail server and is not adequate for that
 * purpose; it simply stores all mail in memory. Use the MessageHandlerFactory
 * interface (optionally with the SimpleMessageListenerAdapter) of SubEthaSMTP
 * instead.
 *
 * @author Jon Stevens
 * @author Jeff Schnitzer
 */
public final class Wiser implements SimpleMessageListener {

    private final static Logger log = LoggerFactory.getLogger(Wiser.class);

    private final SMTPServer server;

    private final List<WiserMessage> messages = new CopyOnWriteArrayList<>();

    private final Accepter accepter;

    public static Wiser port(int port) {
        return create(SMTPServer.port(port));
    }

    public static Wiser create(Builder builder) {
        return new Wiser(builder, ACCEPTER_DEFAULT);
    }

    public static Wiser create() {
        return new Wiser(SMTPServer.port(25), ACCEPTER_DEFAULT);
    }

    public static WiserBuilder accepter(Accepter accepter) {
        return new WiserBuilder().accepter(accepter);
    }

    private static final Accepter ACCEPTER_DEFAULT = (from, recipient) -> {
        log.debug("Accepting mail from {} to {}", from, recipient);
        return true;
    };

    public static final class WiserBuilder {
        private Accepter accepter = ACCEPTER_DEFAULT;
        private Builder server;

        private WiserBuilder() {

        }

        public WiserBuilder accepter(Accepter accepter) {
            this.accepter = accepter;
            return this;
        }

        public Wiser server(SMTPServer.Builder server) {
            this.server = server;
            return new Wiser(server, accepter);
        }

        public Wiser port(int port) {
            this.server = SMTPServer.port(port);
            return new Wiser(server, accepter);
        }

    }

    public static interface Accepter {
        boolean accept(String from, String recipient);
    }

    private Wiser(Builder builder, Accepter accepter) {
        SimpleMessageListener s = new SimpleMessageListener() {

            @Override
            public boolean accept(String from, String recipient) {
                return Wiser.this.accept(from, recipient);
            }

            @Override
            public void deliver(String from, String recipient, InputStream data)
                    throws TooMuchDataException, IOException {
                Wiser.this.deliver(from, recipient, data);
            }
        };
        this.server = builder.simpleMessageListener(s).build();
        this.accepter = accepter;
    }

    /** Starts the SMTP Server */
    public void start() {
        this.server.start();
    }

    /** Stops the SMTP Server */
    public void stop() {
        this.server.stop();
    }

    /** Always accept everything */
    @Override
    public boolean accept(String from, String recipient) {
        return accepter.accept(from, recipient);
    }

    /** Cache the messages in memory */
    @Override
    public void deliver(String from, String recipient, InputStream data)
            throws TooMuchDataException, IOException {
        log.debug("Delivering mail from {} to {}", from, recipient);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        data = new BufferedInputStream(data);

        // read the data from the stream
        int current;
        while ((current = data.read()) >= 0) {
            out.write(current);
        }

        byte[] bytes = out.toByteArray();

        log.debug("Creating message from data with {} bytes", bytes.length);

        Session session = Session.getDefaultInstance(new Properties());
        // create a new WiserMessage.
        this.messages.add(new WiserMessage(session, from, recipient, bytes));
    }

    /**
     * Returns the list of WiserMessages.
     * <p>
     * The number of mail transactions and the number of mails may be different.
     * If a message is received with multiple recipients in a single mail
     * transaction, then the list will contain more WiserMessage instances, one
     * for each recipient.
     */
    public List<WiserMessage> getMessages() {
        return this.messages;
    }

    /**
     * @return the server implementation
     */
    public SMTPServer getServer() {
        return this.server;
    }

    /**
     * For debugging purposes, dumps a rough outline of the messages to the
     * output stream.
     */
    public void dumpMessages(PrintStream out) throws MessagingException {
        out.println("----- Start printing messages -----");

        for (WiserMessage wmsg : this.getMessages())
            wmsg.dumpMessage(out);

        out.println("----- End printing messages -----");
    }

    /** A main() for this class. Starts up the server. */
    public static void main(String[] args) throws Exception {
        Wiser wiser = Wiser.create();
        wiser.start();
    }
}
