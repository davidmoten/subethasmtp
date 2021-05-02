package org.subethamail.smtp.helper;

import org.subethamail.smtp.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

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
        private List<String> recipients = new ArrayList<>();

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
            this.recipients.add(recipient);
        }

        @Override
        public String data(InputStream is) throws RejectException, TooMuchDataException, IOException {
            try {
                byte[] bytes = readAndClose(is, maxMessageSize);

                // must call listener here because if called from done() then
                // a 250 ok response has already been sent
                if (from == null) {
                    throw new RejectException("from not set");
                }
                if (recipients.isEmpty()) {
                    throw new RejectException("recipients not set");
                }
                listener.messageArrived(context, from, recipients, bytes);

                return null;
            } catch (RuntimeException e) {
                throw new RejectException("message could not be accepted: " + e.getMessage());
            }
        }

        private static byte[] readAndClose(InputStream is, int maxMessageSize)
                throws IOException, TooMuchDataException {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            try {
                while ((n = is.read(buffer)) != -1) {
                    bytes.write(buffer, 0, n);
                    if (maxMessageSize > 0 && bytes.size() > maxMessageSize) {
                        throw new TooMuchDataException("message size exceeded maximum of " + maxMessageSize + "bytes");
                    }
                }
            } finally {
                // TODO creator of stream should close it, not this method
                is.close();
            }
            return bytes.toByteArray();
        }

        @Override
        public void done() {
            // do nothing
        }

    }

}
