package org.subethamail.smtp.internal.command;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.subethamail.smtp.DropConnectionException;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.internal.io.DotTerminatedInputStream;
import org.subethamail.smtp.internal.io.DotUnstuffingInputStream;
import org.subethamail.smtp.internal.io.ReceivedHeaderStream;
import org.subethamail.smtp.internal.util.SMTPResponseHelper;
import org.subethamail.smtp.internal.server.BaseCommand;
import org.subethamail.smtp.server.SMTPServer;
import org.subethamail.smtp.server.Session;

import javax.annotation.Nonnull;

/**
 * @author Ian McFarland &lt;ian@neo.com&gt;
 * @author Jon Stevens
 * @author Jeff Schnitzer
 */
public final class DataCommand extends BaseCommand {
    private final static int BUFFER_SIZE = 1024 * 32; // 32k seems reasonable

    public DataCommand() {
        super("DATA", "Following text is collected as the message.\n"
                + "End data with <CR><LF>.<CR><LF>");
    }

    @Override
    public void execute(String commandString, Session sess)
            throws IOException, DropConnectionException {
        if (!sess.isMailTransactionInProgress()) {
            sess.sendResponse("503 5.5.1 Error: need MAIL command");
            return;
        } else if (sess.getRecipientCount() == 0) {
            sess.sendResponse("503 Error: need RCPT command");
            return;
        }

        sess.sendResponse("354 End data with <CR><LF>.<CR><LF>");

        String dataMessage = null;
        try (InputStream stream = getInputStream(sess)) {
            dataMessage = sess.getMessageHandler().data(stream);

            // Just in case the handler didn't consume all the data, we might as
            // well suck it up so it doesn't pollute further exchanges. This
            // code used to throw an exception, but this seems an arbitrary part
            // of the contract that we might as well relax.
            while (stream.read() != -1)
                ;

        } catch (DropConnectionException ex) {
            throw ex; // Propagate this
        } catch (RejectException ex) {
            sess.sendResponse(ex.getErrorResponse());
            return;
        }

        if (dataMessage!= null) {
            sess.sendResponse(SMTPResponseHelper.buildResponse("250", dataMessage));
        } else {
            sess.sendResponse("250 Ok");
        }
        sess.resetMailTransaction();
    }

    private @Nonnull InputStream getInputStream(Session sess) {
        InputStream stream = new BufferedInputStream(sess.getRawInput(), BUFFER_SIZE);
        stream = new DotTerminatedInputStream(stream);
        stream = new DotUnstuffingInputStream(stream);
        SMTPServer server = sess.getServer();

        return server.getDisableReceivedHeaders() ? stream : new ReceivedHeaderStream(stream, sess.getHelo(),
                sess.getRemoteAddress().getAddress(), server.getHostName(),
                Optional.of(server.getSoftwareName()), sess.getSessionId(),
                sess.getSingleRecipient());
    }
}
