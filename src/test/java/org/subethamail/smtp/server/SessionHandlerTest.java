package org.subethamail.smtp.server;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import org.junit.Test;
import org.mockito.Mockito;
import org.subethamail.smtp.internal.proxy.ProxyHandler;
import org.subethamail.smtp.internal.server.ServerThread;

public class SessionHandlerTest {

    @Test
    public void testAcceptAll() throws IOException {
        SessionHandler h = SessionHandler.acceptAll();
        SMTPServer server = SMTPServer.port(2020).build();
        try (ServerSocket ss = new ServerSocket(0)) {
            ServerThread serverThread = new ServerThread(server, ss, ProxyHandler.NOP);
            Socket socket = Mockito.mock(Socket.class);
            ByteArrayInputStream in = new ByteArrayInputStream(
                    "hi there".getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Mockito.when(socket.getInputStream()).thenReturn(in);
            Mockito.when(socket.getOutputStream()).thenReturn(out);
            Session session = new Session(server, serverThread, socket, ProxyHandler.NOP);
            assertTrue(h.accept(session).accepted());
        }
    }


    @Test
    public void testMultipleMailsInOneSessionInputNotClosed() throws Exception {
        SessionHandler h = SessionHandler.acceptAll();
        SMTPServer server = SMTPServer.port(2021).build();

        try (ServerSocket ss = new ServerSocket(0)) {
            ServerThread serverThread = new ServerThread(server, ss, ProxyHandler.NOP);
            Socket socket = Mockito.mock(Socket.class);

            // Два письма подряд
            byte[] inputData = ("MAIL FROM:<a@test>\r\n" +
                    "RCPT TO:<b@test>\r\n" +
                    "DATA\r\n" +
                    "Hello World\r\n.\r\n" +   // first letter end
                    "MAIL FROM:<c@test>\r\n" +
                    "RCPT TO:<d@test>\r\n" +
                    "DATA\r\n" +
                    "Second mail\r\n.\r\n")    // second letter
                    .getBytes(StandardCharsets.UTF_8);

            ByteArrayInputStream in = new ByteArrayInputStream(inputData);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            Mockito.when(socket.getInputStream()).thenReturn(in);
            Mockito.when(socket.getOutputStream()).thenReturn(out);

            Session session = new Session(server, serverThread, socket, ProxyHandler.NOP);
            assertTrue(h.accept(session).accepted());

            // close input
            session.getRawInput().close();

            // socker should work
            assertTrue("Socket must remain open", !socket.isClosed());

            // check we can read next letter
            int nextByte = session.getRawInput().read();
            assertTrue("Should still be able to read from input", nextByte != -1);
        }
    }

}
