package org.subethamail.smtp.internal.command;

import java.io.IOException;
import java.io.InputStream;

import org.subethamail.smtp.DropConnectionException;
import org.subethamail.smtp.RejectException;
import org.subethamail.smtp.internal.io.BdatInputStream;
import org.subethamail.smtp.internal.server.BaseCommand;
import org.subethamail.smtp.internal.util.SMTPResponseHelper;
import org.subethamail.smtp.server.Session;

/**
 * @author David Moten
 */
public final class BdatCommand extends BaseCommand {

    public BdatCommand() {
        super("BDAT", "A sequence of BDAT packets is collected as the data of the message.");
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

        Bdat bdat = parse(commandString);
        if (bdat.errorMessage != null) {
            sess.sendResponse(bdat.errorMessage);
            return;
        }


        String dataMessage = null;
        try (InputStream stream = new BdatInputStream(sess.getRawInput(), sess, bdat.size, bdat.isLast)) {
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

        if (dataMessage != null) {
            sess.sendResponse(SMTPResponseHelper.buildResponse("250", dataMessage));
        } else {
            sess.sendResponse("250 Ok");
        }
        sess.resetMailTransaction();
    }

    public static Bdat parse(String commandString) {
        String[] args = getArgs(commandString);
        if (args.length == 1) {
            return new Bdat("503 Error: wrong syntax for BDAT command");
        }
        long size;
        try {
            size = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            return new Bdat("503 Error: integer size expected after BDAT token");
        }
        if (size < 0) {
            return new Bdat("503 Error: size token after BDAT must be non-negative integer");
        }
        if (args.length == 3 && !"LAST".equals(args[2])) {
            return new Bdat("503 Error: expected LAST but found " + args[2]);
        }
        if (args.length > 3) {
            return new Bdat("503 Error: too many arguments found for BDAT command");
        }

        boolean isLast = args.length == 3 && "LAST".equals(args[2]);

        if (size == 0 && !isLast) {
            return new Bdat("551 5.7.1 Error: Null BDAT request");
        }

        return new Bdat(size, isLast);
    }

    public static final class Bdat {
        public final long size;
        public final boolean isLast;
        public final String errorMessage;

        private Bdat(long size, boolean isLast, String errorMessage) {
            this.size = size;
            this.isLast = isLast;
            this.errorMessage = errorMessage;
        }

        Bdat(long size, boolean isLast) {
            this(size, isLast, null);
        }

        Bdat(String errorMessage) {
            this(0, true, errorMessage);
        }
    }
}
