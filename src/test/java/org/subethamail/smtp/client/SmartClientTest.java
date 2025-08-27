package org.subethamail.smtp.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.subethamail.smtp.TestUtil.createTlsSslContext;
import static org.subethamail.smtp.TestUtil.getKeyManagers;
import static org.subethamail.smtp.TestUtil.getTrustManagers;

import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.security.KeyStoreException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Optional;
import java.util.Set;
import javax.net.ssl.SSLContext;

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

    @Test
    public void testStartTLS() throws SMTPException, IOException , KeyStoreException, KeyManagementException, NoSuchAlgorithmException, CertificateException, UnrecoverableKeyException {
        SSLContext sslContext = createTlsSslContext(getKeyManagers(), getTrustManagers());

        SMTPServer server = SMTPServer.port(25000)
                .enableTLS(true)
                .startTlsSocketFactory(sslContext)
                .messageHandlerFactory(createMessageHandlerFactory())
                .build();
        try {
            server.start();
            SmartClient client = SmartClient.createAndConnect("localhost",
                    25000,
                    Optional.empty(),
                    "clientHeloHost",
                    Optional.empty(),
                    Optional.of(sslContext.getSocketFactory())
            );

            assertEquals("clientHeloHost", client.getHeloHost());
            assertEquals(0, client.getRecipientCount());
            assertFalse(client.getAuthenticator().isPresent());
            assertEquals(5, client.getExtensions().size());
            Set<String> set = client.getExtensions().keySet();
            assertTrue(set.contains("8BITMIME"));
            assertTrue(set.contains("CHUNKING"));
            assertTrue(set.contains("SMTPUTF8"));
            assertTrue(set.contains("STARTTLS"));

            client.startTLS();
            assertEquals(5, client.getExtensions().size());
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
