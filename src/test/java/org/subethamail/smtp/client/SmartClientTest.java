package org.subethamail.smtp.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;
import org.subethamail.smtp.server.SMTPServer;

public class SmartClientTest {

    @Test
    public void test() throws InterruptedException, UnknownHostException, SMTPException, IOException {
        SMTPServer server = SMTPServer.port(25000).messageHandlerFactory(createMessageHandlerFactory()).build();
        try {
            server.start();
            SmartClient client = SmartClient.createAndConnect("localhost", 25000, "clientHeloHost");
            assertEquals("clientHeloHost", client.getHeloHost());
            assertEquals(0, client.getRecipientCount());
            Assert.assertFalse(client.getAuthenticator().isPresent());
            assertEquals(4, client.getExtensions().size());
            Set<String> set = client.getExtensions().keySet();
            assertTrue(set.contains("8BITMIME"));
            assertTrue(set.contains("CHUNKING"));
            assertTrue(set.contains("SMTPUTF8"));
            //TODO why is OK in client.getExtensions?
        } finally {
            server.stop();
        }

    }

    private MessageHandlerFactory createMessageHandlerFactory() {
        return new MessageHandlerFactory() {

            @Override
            public MessageHandler create(MessageContext ctx) {
                return new MessageHandler() {

                    @Override
                    public void from(String from) throws RejectException {
                    }

                    @Override
                    public void recipient(String recipient) throws RejectException {
                    }

                    @Override
                    public String data(InputStream data) throws RejectException, TooMuchDataException, IOException {
                        return null;
                    }

                    @Override
                    public void done() {
                    }};
            }
        };
    }

}
