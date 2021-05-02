package org.subethamail.smtp;

import com.sun.mail.smtp.SMTPSendFailedException;
import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.*;
import org.junit.Test;
import org.subethamail.smtp.helper.BasicMessageListener;
import org.subethamail.smtp.server.SMTPServer;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class ErrorResponseTest {

    static final String LOCALHOST = "127.0.0.1";
    static final String EMAIL_TO = "me@gmail.com";
    static final String EMAIL_FROM = "fred@gmail.com";
    static final int PORT = 25000;

    @Test
    public void test() throws AddressException, MessagingException {
        AtomicBoolean accepted = new AtomicBoolean();
        BasicMessageListener listener = new BasicMessageListener() {

            int count = 0;

            @Override
            public void messageArrived(MessageContext context, String from, List<String> to, byte[] data)
                    throws RejectException {
                count++;
                if (count == 1) {
                    throw new RejectException("first request rejected");
                }
                accepted.set(true);
            }
        };
        SMTPServer server = SMTPServer //
                .port(PORT) //
                .hostName("email-server.me.com") //
                .messageHandler(listener) //
                .executorService(Executors.newSingleThreadExecutor()) //
                .build();
        try {
            server.start();
            try {
                send(PORT);
                fail();
            } catch (SMTPSendFailedException e) {
                // first message should fail
                assertEquals(554, e.getReturnCode());
            }
            // second message succeeds
            send(PORT);
            assertTrue(accepted.get());
        } finally {
            server.stop();
        }
    }

    private static Session createSession(int port) {

        String host = LOCALHOST;
        Properties props = new Properties();
        props.put("mail.debug", "true");
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", PORT + "");
        return Session.getInstance(props);
    }

    private void send(int port) throws AddressException, MessagingException {
        String to = EMAIL_TO;
        String from = EMAIL_FROM;
        Session session = createSession(port);
        // Create a default MimeMessage object.
        Message message = new MimeMessage(session);

        // Set From: header field of the header.
        message.setFrom(new InternetAddress(from));

        InternetAddress[] toAddresses = InternetAddress.parse(to);

        // Set To: header field of the header.
        message.setRecipients(Message.RecipientType.TO, toAddresses);

        // Set Subject: header field
        message.setSubject("Testing Subject " + "\u2191");

        // Create the message part
        BodyPart messageBodyPart = new MimeBodyPart();

        // Now set the actual message
        messageBodyPart.setText("This is message body");

        // Create a multipar message
        Multipart multipart = new MimeMultipart();

        // Set text message part
        multipart.addBodyPart(messageBodyPart);

        // Part two is attachment
        messageBodyPart = new MimeBodyPart();
        DataSource source = new FileDataSource(new File("src/test/resources/man.png"));
        messageBodyPart.setDataHandler(new DataHandler(source));
        messageBodyPart.setFileName("man" + "\u2191" + ".png");
        multipart.addBodyPart(messageBodyPart);

        // Send the complete message parts
        message.setContent(multipart);

        Transport.send(message);
    }

}
