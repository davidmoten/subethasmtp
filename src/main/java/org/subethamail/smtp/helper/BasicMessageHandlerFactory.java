package org.subethamail.smtp.helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandler;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.TooMuchDataException;

public class BasicMessageHandlerFactory implements MessageHandlerFactory {

    private final BasicMessageListener listener;
    private final int maxMessageSize;

    public BasicMessageHandlerFactory(BasicMessageListener listener, int maxMessageSize) {
        this.listener = listener;
        this.maxMessageSize = maxMessageSize;
    }

    @Override
    public MessageHandler create(MessageContext context) {
        return new BasicMessageHandler(context, listener, maxMessageSize);
    }

    public static class BasicMessageHandler implements MessageHandler {

        private final BasicMessageListener listener;

        private String from;
        private String recipient;

        private final MessageContext context;
        private final int maxMessageSize;


        public BasicMessageHandler(MessageContext context, BasicMessageListener listener, int maxMessageSize) {
            this.context = context;
            this.listener = listener;
            this.maxMessageSize = maxMessageSize;
        }

        @Override
        public void from(String from) throws RejectException {
            this.from = from;

        }

        @Override
        public void recipient(String recipient) throws RejectException {
            this.recipient = recipient;
        }

        @Override
        public String data(InputStream is) throws RejectException, TooMuchDataException, IOException {
            try {
                byte[] bytes = read(is, maxMessageSize);

                // must call listener here because if called from done() then
                // a 250 ok response has already been sent
                if (from == null) {
                    throw new RejectException("from not set");
                }
                if (recipient == null) {
                    throw new RejectException("recipient not set");
                }
                listener.messageArrived(context, from, recipient, bytes);

                return null;
            } catch (RuntimeException e) {
                throw new RejectException("message could not be accepted: " + e.getMessage());
            }
        }

        /**
         * Convert provided InputStream to byte array and check that InputStream is not more than maxMessageSize
         * @param is will be converted to array
         * @param maxMessageSize limit of message
         * @return converted InputStream to byte array
         * @throws IOException
         * @throws TooMuchDataException if the data size is greater than the limit
         */
        private static byte[] read(InputStream is, int maxMessageSize)
                throws IOException, TooMuchDataException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;

            while ((n = is.read(buffer)) != -1) {
                bytes.write(buffer, 0, n);
                if (maxMessageSize > 0 && bytes.size() > maxMessageSize) {
                    throw new TooMuchDataException("message size exceeded maximum of " + maxMessageSize + "bytes");
                }
            }

            return bytes.toByteArray();
        }

        @Override
        public void done() {
            // do nothing
        }

    }

}
