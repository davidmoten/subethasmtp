package org.subethamail.smtp;

import org.junit.Ignore;
import org.junit.Test;
import org.subethamail.smtp.client.SMTPException;
import org.subethamail.smtp.client.SmartClient;
import org.subethamail.smtp.helper.BasicMessageListener;
import org.subethamail.smtp.server.SMTPServer;

import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BdatTest {

    @Test
    public void testOneBdatCommand() throws UnknownHostException, SMTPException, IOException {
        MyListener listener = new MyListener();
        SMTPServer server = SMTPServer.port(25000).messageHandler(listener).build();
        try {
            server.start();
            SmartClient client = SmartClient.createAndConnect("localhost", 25000, "clientHeloHost");
            assertTrue(client.getExtensions().containsKey("CHUNKING"));
            client.from("me@oz.com");
            client.to("dave@oz.com");
            client.bdatLast("hello");
            assertEquals("hello", listener.dataAsText());
            assertEquals("me@oz.com", listener.from);
            assertEquals("dave@oz.com", listener.to);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testBadBdatCommand() throws UnknownHostException, SMTPException, IOException {
        MyListener listener = new MyListener();
        SMTPServer server = SMTPServer.port(25000).messageHandler(listener).build();
        try {
            server.start();
            SmartClient client = SmartClient.createAndConnect("localhost", 25000, "clientHeloHost");
            assertTrue(client.getExtensions().containsKey("CHUNKING"));
            client.from("me@oz.com");
            client.to("dave@oz.com");
            client.bdat("hello");
            try {
                client.sendAndCheck("BDAT \r\n");
            } catch (SMTPException e) {
                assertEquals("503 Error: wrong syntax for BDAT command", e.getMessage());
            }
        } finally {
            server.stop();
        }
    }

    @Test
    public void testTwoBdatCommands() throws UnknownHostException, SMTPException, IOException {
        MyListener listener = new MyListener();
        SMTPServer server = SMTPServer.port(25000).messageHandler(listener).build();
        try {
            server.start();
            SmartClient client = SmartClient.createAndConnect("localhost", 25000, "clientHeloHost");
            assertTrue(client.getExtensions().containsKey("CHUNKING"));
            client.from("me@oz.com");
            client.to("dave@oz.com");
            client.bdat("hello");
            client.bdatLast("there");
            assertEquals("hellothere", listener.dataAsText());
            assertEquals("me@oz.com", listener.from);
            assertEquals("dave@oz.com", listener.to);
        } finally {
            server.stop();
        }
    }

    @Test
    public void testTwoBdatFollowedBySomethingElse() throws UnknownHostException, SMTPException, IOException {
        MyListener listener = new MyListener();
        SMTPServer server = SMTPServer.port(25000).messageHandler(listener).build();
        try {
            server.start();
            SmartClient client = SmartClient.createAndConnect("localhost", 25000, "clientHeloHost");
            assertTrue(client.getExtensions().containsKey("CHUNKING"));
            client.from("me@oz.com");
            client.to("dave@oz.com");
            client.bdat("hello");
            try {
                client.from("shouldFail");
            } catch (SMTPException e) {
                assertEquals("503 Error: expected BDAT command line but encountered: 'MAIL FROM: <shouldFail>'",
                        e.getMessage());
            }
        } finally {
            server.stop();
        }
    }

    @Test
    @Ignore
    public void testTwoMailsWithBdatInSameSession()
            throws UnknownHostException, SMTPException, IOException, InterruptedException {
        MyListener listener = new MyListener();
        SMTPServer server = SMTPServer.port(25000).messageHandler(listener).build();
        try {
            server.start();
            SmartClient client = SmartClient.createAndConnect("localhost", 25000, "clientHeloHost");
            assertTrue(client.getExtensions().containsKey("CHUNKING"));
            client.from("me@oz.com");
            client.to("dave@oz.com");
            client.bdat("hello");
            client.bdatLast("there");
            assertEquals("hellothere", listener.dataAsText());
            assertEquals("me@oz.com", listener.from);
            assertEquals("dave@oz.com", listener.to);

            // Note that a second mail message doesn't work because BdatCommand and
            // DataCommand both suck up the whole session input stream
            // i.e. subethasmtp doesn't expect more than one message in the one session
            // TODO support multiple mail messages in the same session?

            client.from("me2@oz.com");
            client.to("dave2@oz.com");
            client.bdat("hello2");
            client.bdatLast("there2");
            assertEquals(2, listener.count);
            assertEquals("hello2there2", listener.dataAsText());
            assertEquals("me2@oz.com", listener.from);
            assertEquals("dave2@oz.com", listener.to);
        } finally {
            server.stop();
        }
    }

    static final class MyListener implements BasicMessageListener {

        String from;
        List<String> to;
        byte[] data;

        int count = 0;

        @Override
        public void messageArrived(MessageContext context, String from, List<String> to, byte[] data) throws RejectException {
            this.from = from;
            this.to = to;
            this.data = data;
            count++;
            System.out.println("count="+ count + ", data=" + dataAsText());
        }

        String dataAsText() {
            if (data == null) {
                return null;
            } else {
                return new String(data, StandardCharsets.UTF_8);
            }
        }

    }

}
