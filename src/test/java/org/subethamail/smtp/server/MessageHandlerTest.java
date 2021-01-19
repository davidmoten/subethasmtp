package org.subethamail.smtp.server;

import java.io.IOException;
import java.io.InputStream;

import jakarta.mail.MessagingException;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.client.SMTPException;
import org.subethamail.smtp.client.SmartClient;
import org.subethamail.smtp.internal.util.TextUtils;

/**
 * This class tests whether the event handler methods defined in MessageHandler
 * are called at the appropriate times and in good order.
 */
public class MessageHandlerTest {

    private static SMTPServer create(MessageHandlerFactory f) {
        SMTPServer server = SMTPServer.port(2566).messageHandlerFactory(f).build();
        server.start();
        return server;
    }

    @Test
    public void testCompletedMailTransaction() throws Exception {
        MessageHandlerFactory f = Mockito.mock(MessageHandlerFactory.class);
        MessageHandler h = Mockito.mock(MessageHandler.class);
        Mockito.when(f.create(ArgumentMatchers.any(MessageContext.class))).thenReturn(h);
        SMTPServer server = create(f);
        try {
            SmartClient client = SmartClient.createAndConnect("localhost", server.getPort(), "localhost");
            client.from("john@example.com");
            client.to("jane@example.com");
            client.dataStart();
            client.dataWrite(TextUtils.getAsciiBytes("body"), 4);
            client.dataEnd();
            client.quit();
        } finally {
            server.stop(); // wait for the server to catch up
        }
        InOrder o = Mockito.inOrder(f, h);
        o.verify(f).create(ArgumentMatchers.any(MessageContext.class));
        o.verify(h).from("john@example.com");
        o.verify(h).recipient("jane@example.com");
        o.verify(h).data(ArgumentMatchers.any(InputStream.class));
        o.verify(h).done();
        Mockito.verifyNoMoreInteractions(f, h);
    }

    @Test
    public void testDisconnectImmediately() throws Exception {
        MessageHandlerFactory f = Mockito.mock(MessageHandlerFactory.class);
        SMTPServer server = create(f);
        try {
            SmartClient client = SmartClient.createAndConnect("localhost", server.getPort(), "localhost");
            client.quit();
        } finally {
            server.stop();
        }
        Mockito.verifyNoMoreInteractions(f);
    }

    @Test
    public void testAbortedMailTransaction() throws Exception {
        MessageHandlerFactory f = Mockito.mock(MessageHandlerFactory.class);
        MessageHandler h = Mockito.mock(MessageHandler.class);
        Mockito.when(f.create(ArgumentMatchers.any(MessageContext.class))).thenReturn(h);
        SMTPServer server = create(f);
        try {
            SmartClient client = SmartClient.createAndConnect("localhost", server.getPort(), "localhost");
            client.from("john@example.com");
            client.quit();
        } finally {
            server.stop(); // wait for the server to catch up
        }
        InOrder o = Mockito.inOrder(f, h);
        o.verify(f).create(ArgumentMatchers.any(MessageContext.class));
        o.verify(h).from("john@example.com");
        o.verify(h).done();
        Mockito.verifyNoMoreInteractions(f, h);
    }

    @Test
    public void testTwoMailsInOneSession() throws Exception {

        MessageHandlerFactory f = Mockito.mock(MessageHandlerFactory.class);
        MessageHandler h = Mockito.mock(MessageHandler.class);
        Mockito.when(f.create(ArgumentMatchers.any(MessageContext.class))).thenReturn(h);
        SMTPServer server = create(f);
        try {
            SmartClient client = SmartClient.createAndConnect("localhost", server.getPort(), "localhost");

            client.from("john1@example.com");
            client.to("jane1@example.com");
            client.dataStart();
            client.dataWrite(TextUtils.getAsciiBytes("body1"), 5);
            client.dataEnd();

            client.from("john2@example.com");
            client.to("jane2@example.com");
            client.dataStart();
            client.dataWrite(TextUtils.getAsciiBytes("body2"), 5);
            client.dataEnd();
            client.quit();
        } finally {
            server.stop(); // wait for the server to catch up
        }
        InOrder o = Mockito.inOrder(f, h);
        o.verify(f).create(ArgumentMatchers.any(MessageContext.class));
        o.verify(h).from("john1@example.com");
        o.verify(h).recipient("jane1@example.com");
        o.verify(h).data(ArgumentMatchers.any(InputStream.class));
        o.verify(h).done();
        o.verify(f).create(ArgumentMatchers.any(MessageContext.class));
        o.verify(h).from("john2@example.com");
        o.verify(h).recipient("jane2@example.com");
        o.verify(h).data(ArgumentMatchers.any(InputStream.class));
        o.verify(h).done();
        Mockito.verifyNoMoreInteractions(f, h);
    }

    /**
     * Test for issue 56: rejecting a Mail From causes IllegalStateException in
     * the next Mail From attempt.
     * 
     * @throws RejectException
     * @see <a
     *      href=http://code.google.com/p/subethasmtp/issues/detail?id=56>Issue
     *      56</a>
     */
    @Test
    public void testMailFromRejectedFirst() throws IOException, MessagingException, RejectException {

        MessageHandlerFactory f = Mockito.mock(MessageHandlerFactory.class);
        MessageHandler h = Mockito.mock(MessageHandler.class);
        Mockito.doThrow(new RejectException("Test MAIL FROM rejection")).when(h).from("john1@example.com");
        Mockito.doNothing().when(h).from("john2@example.com");
        Mockito.when(f.create(ArgumentMatchers.any(MessageContext.class))).thenReturn(h);
        SMTPServer server = create(f);
        try {
            SmartClient client = SmartClient.createAndConnect("localhost", server.getPort(), "localhost");
            try {
                client.from("john1@example.com");
                Assert.fail();
            } catch (SMTPException e) {
                // expected
            }
            client.from("john2@example.com");
            client.quit();
        } finally {
            server.stop(); // wait for the server to catch up
        }
        InOrder o = Mockito.inOrder(f, h);
        o.verify(f).create(ArgumentMatchers.any(MessageContext.class));
        o.verify(h).from("john1@example.com");
        o.verify(h).done();
        o.verify(f).create(ArgumentMatchers.any(MessageContext.class));
        o.verify(h).from("john2@example.com");
        o.verify(h).done();
        Mockito.verifyNoMoreInteractions(f, h);

    }

}
