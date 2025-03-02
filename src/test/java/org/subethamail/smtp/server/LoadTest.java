package org.subethamail.smtp.server;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.subethamail.smtp.helper.BasicMessageListener;

import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

public class LoadTest {

    private final static int messages = 5000000;
    private final static int port = 1234;

    public static final class Server {

        public static void main(String[] args) throws Exception {
            CountDownLatch latch = new CountDownLatch(messages);
            long[] startTime = new long[1];
            BasicMessageListener handler = (context, from, to, data) -> {
                if (startTime[0] == 0) {
                    startTime[0] = System.currentTimeMillis();
                }
                latch.countDown();
            };
            SMTPServer server = SMTPServer //
                    .port(port) //
                    .messageHandler(handler) //
                    .build();
            server.start();
            latch.await();
            server.stop(); // wait for the server to catch up

            long t = (System.currentTimeMillis() - startTime[0]);
            System.out.println(1000.0 * messages * messages / (messages - 1) / t + " messages/s");
            System.out.println("total time = " + t/1000 + "s");
        }
    }

    public static final class Client {

        public static void main(String[] args) throws MessagingException, InterruptedException {
            String to = "you@yours.com";
            String from = "me@mine.com";
            String host = "localhost";
            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", port + "");

            jakarta.mail.Session session = jakarta.mail.Session.getInstance(props);
            ExecutorService executor = Executors.newFixedThreadPool(20);

            for (int i = 0; i < messages; i++) {
                int number = i;
                executor.submit(() -> {
                    try {
                        // Create a default MimeMessage object.
                        Message message = new MimeMessage(session);

                        // Set From: header field of the header.
                        message.setFrom(new InternetAddress(from));

                        InternetAddress[] toAddresses = InternetAddress.parse(to);

                        // Set To: header field of the header.
                        message.setRecipients(Message.RecipientType.TO, toAddresses);

                        // Create the message part
                        BodyPart messageBodyPart = new MimeBodyPart();

                        // Now set the actual message
                        messageBodyPart.setText("This is message body");

                        // Create a multipar message
                        Multipart multipart = new MimeMultipart();

                        // Set text message part
                        multipart.addBodyPart(messageBodyPart);

                        // Send the complete message parts
                        message.setContent(multipart);

                        message.setSubject("Testing Subject " + number);

                        Transport.send(message);
                    } catch (MessagingException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.DAYS);
            System.out.println("sent");
        }
    }
}
