package org.subethamail.smtp.server;

import java.nio.charset.StandardCharsets;
import org.junit.Assert;
import org.junit.Test;
import org.subethamail.smtp.client.SMTPException;
import org.subethamail.smtp.client.SmartClient;
import org.subethamail.smtp.internal.util.TextUtils;

public class SourceTrackingSessionHandlerTest {

    private static SMTPServer create(int max) {
        SMTPServer server = SMTPServer.port(0).messageHandler(
                (context, from, to,
                        data) -> System.out.println("message from " + from + " to " + to
                                + ":\n" + new String(data, StandardCharsets.UTF_8)))
                .sessionHandler(new ConcurrentSessionsBySourceLimiter(max))
                .build();
        server.start();
        return server;
    }


    @Test
    public void twoSequentially() throws Exception {
        final SMTPServer server = create(1);
        try {
            SmartClient client1 = SmartClient.createAndConnect("localhost", server.getPortAllocated(), "localhost");

            client1.from("john1@example.com");
            client1.to("jane1@example.com");
            client1.dataStart();
            client1.dataWrite(TextUtils.getAsciiBytes("body"), 4);
            client1.dataEnd();
            client1.quit();

            SmartClient client2 = SmartClient.createAndConnect("localhost", server.getPortAllocated(), "localhost");

            client2.from("john2@example.com");
            client2.to("jane2@example.com");
            client2.dataStart();
            client2.dataWrite(TextUtils.getAsciiBytes("body"), 4);
            client2.dataEnd();
            client2.quit();
        } finally {
            server.stop(); // wait for the server to catch up
        }
    }

    @Test
    public void twoConcurrently() throws Exception {
        final SMTPServer server = create(2);
        try {
            SmartClient client1 = SmartClient.createAndConnect("localhost", server.getPortAllocated(), "localhost");
            SmartClient client2 = SmartClient.createAndConnect("localhost", server.getPortAllocated(), "localhost");

            client1.from("john1@example.com");
            client1.to("jane1@example.com");
            client1.dataStart();
            client1.dataWrite(TextUtils.getAsciiBytes("body"), 4);
            client1.dataEnd();
            client1.quit();

            client2.from("john2@example.com");
            client2.to("jane2@example.com");
            client2.dataStart();
            client2.dataWrite(TextUtils.getAsciiBytes("body"), 4);
            client2.dataEnd();
            client2.quit();
        } finally {
            server.stop(); // wait for the server to catch up
        }
    }

    @Test
    public void twoConcurrentlyReject() throws Exception {
        final SMTPServer server = create(1);
        try {
            SmartClient client1 = SmartClient.createAndConnect("localhost", server.getPortAllocated(), "localhost");
            try {
                SmartClient.createAndConnect("localhost", server.getPortAllocated(), "localhost");
                Assert.fail("Client should fail on opening");
            } catch (SMTPException e) {
                Assert.assertTrue(e.getMessage().startsWith("421 Too many connections"));
            }

            client1.from("john1@example.com");
            client1.to("jane1@example.com");
            client1.dataStart();
            client1.dataWrite(TextUtils.getAsciiBytes("body"), 4);
            client1.dataEnd();
            client1.quit();
        } finally {
            server.stop(); // wait for the server to catch up
        }
    }

}
