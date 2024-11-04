package org.subethamail.smtp.internal.io;

import java.io.IOException;
import java.io.InputStream;

import org.subethamail.smtp.internal.command.BdatCommand;
import org.subethamail.smtp.internal.command.BdatCommand.Bdat;
import org.subethamail.smtp.server.Session;

/**
 * @author David Moten
 */
public final class BdatInputStream extends InputStream {

    private final InputStream in;
    private final Session session;

    
    // mutable fields
    private final CRLFTerminatedReader reader;
    private long remainingSize;

    private long size;
    private boolean isLast;
    

    public BdatInputStream(InputStream in, Session session, long size, boolean isLast) {
        this.in = in;
        this.session = session;
        this.reader = new CRLFTerminatedReader(in);
        this.remainingSize = size;
        this.size = size;
        this.isLast = isLast;
    }

    @Override
    public int read() throws IOException {
        // Note that at no point do we close `in` or `reader`. The closure of the
        // inputStream is left to the BdatCommand class and beyond.
        while (true) {
            if (remainingSize > 0) {
                int v = in.read();
                remainingSize--;
                return v;
            } else if (isLast) {
                return -1;
            } else {
                session.sendResponse("250 Message OK, " + size + " bytes received");
                String line = reader.readLine();
                if (line.startsWith("BDAT ")) {
                    Bdat bdat = BdatCommand.parse(line);
                    if (bdat.errorMessage != null) {
                        session.sendResponse(bdat.errorMessage);
                        throw new IOException(bdat.errorMessage);
                    } else {
                        remainingSize = bdat.size;
                        size = bdat.size;
                        isLast = bdat.isLast;
                    }
                } else {
                    String message = "503 Error: expected BDAT command line but encountered: '" + line + "'";
                    session.sendResponse(message);
                    throw new IOException(message);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
