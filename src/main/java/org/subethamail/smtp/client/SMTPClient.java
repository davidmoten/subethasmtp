package org.subethamail.smtp.client;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.internal.Constants;
import org.subethamail.smtp.internal.io.DotTerminatedOutputStream;
import org.subethamail.smtp.internal.io.ExtraDotOutputStream;

import com.github.davidmoten.guavamini.Preconditions;

/**
 * A very low level abstraction of the STMP stream which knows how to handle the
 * raw protocol for lines, whitespace, etc.
 *
 * @author Jeff Schnitzer
 */
public final class SMTPClient {
    /** 5 minutes */
    private static final int CONNECT_TIMEOUT = 300 * 1000;

    /** 10 minutes */
    private static final int REPLY_TIMEOUT = 600 * 1000;

    private static Logger log = LoggerFactory.getLogger(SMTPClient.class);

    /** the local socket address */
    private final Optional<SocketAddress> bindpoint;

    private volatile SocketAddress localSocketAddress;

    /**
     * True if the client has been successfully connected to the server and not
     * it has not been closed yet.
     **/
    private boolean connected;

    /** Just for display purposes */
    private final Optional<String> hostPortName;

    /** The raw socket */
    Socket socket;

    BufferedReader reader;

    /** Output streams used for data */
    OutputStream rawOutput;
    /**
     * A stream which wraps {@link #rawOutput} and is used to write out the DOT
     * CR LF terminating sequence in the DATA command, if necessary
     * complementing the message content with a closing CR LF.
     */
    DotTerminatedOutputStream dotTerminatedOutput;
    /**
     * This stream wraps {@link #dotTerminatedOutput} and it does the dot
     * stuffing for the SMTP DATA command.
     */
    ExtraDotOutputStream dataOutput;

    /** Note we bypass this during DATA */
    PrintWriter writer;

    /**
     * Result of an SMTP exchange.
     */
    public static final class Response {
        int code;
        String message;

        public Response(int code, String text) {
            this.code = code;
            this.message = text;
        }

        public int getCode() {
            return this.code;
        }

        public String getMessage() {
            return this.message;
        }

        public boolean isSuccess() {
            return this.code >= 100 && this.code < 400;
        }

        @Override
        public String toString() {
            return this.code + " " + this.message;
        }
    }

    /**
     * Establishes a connection to host and port.
     * 
     * @throws UnknownHostException
     *             if the hostname cannot be resolved
     * @throws IOException
     *             if there is a problem connecting to the port
     */
    public SMTPClient() throws UnknownHostException, IOException {
        this(Optional.empty(), Optional.empty());
    }

    /**
     * Constructor.
     * 
     * @param bindpoint
     *            the local socket address. If empty, the system will pick up an
     *            ephemeral port and a valid local address.
     * @param hostPortName
     *            Sets the name of the remote MTA for informative purposes.
     *            Default is host:port, where host and port are the values which
     *            were used to open the TCP connection to the server, as they
     *            were passed to the connect method.
     * @throws UnknownHostException
     *             if the hostname cannot be resolved
     * @throws IOException
     *             if there is a problem connecting to the port
     */
    public SMTPClient(Optional<SocketAddress> bindpoint, Optional<String> hostPortName)
            throws UnknownHostException, IOException {
        Preconditions.checkNotNull(bindpoint, "bindpoint cannot be null");
        this.bindpoint = bindpoint;
        this.hostPortName = hostPortName;
    }

    public static SMTPClient createAndConnect(String host, int port)
            throws UnknownHostException, IOException {
        return createAndConnect(host, port, Optional.empty());
    }

    public static SMTPClient createAndConnect(String host, int port, Optional<String> hostPortName)
            throws UnknownHostException, IOException {
        SMTPClient client = new SMTPClient(Optional.empty(),
                Optional.of(hostPortName.orElse(host + ":" + port)));
        client.connect(host, port);
        return client;
    }

    /**
     * Establishes a connection to host and port.
     * 
     * @throws IOException
     *             if there is a problem connecting to the port
     */
    public void connect(String host, int port) throws IOException {
        if (connected)
            throw new IllegalStateException("Already connected");

        log.debug("Connecting to {}", this.hostPortName);

        this.socket = createSocket();
        this.socket.bind(this.bindpoint.orElse(null));
        this.socket.setSoTimeout(REPLY_TIMEOUT);
        this.socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);

        try {
            this.localSocketAddress = this.socket.getLocalSocketAddress();

            this.reader = new BufferedReader(
                    new InputStreamReader(this.socket.getInputStream(), Constants.SMTP_CHARSET));

            this.rawOutput = this.socket.getOutputStream();
            this.dotTerminatedOutput = new DotTerminatedOutputStream(this.rawOutput);
            this.dataOutput = new ExtraDotOutputStream(this.dotTerminatedOutput);
            this.writer = new PrintWriter(this.rawOutput, true);
        } catch (IOException e) {
            close();
            throw e;
        }

        connected = true;
    }

    /**
     * Returns a new unconnected socket.
     * <p>
     * Implementation notice for subclasses: This function is called by the
     * constructors which open the connection immediately. In these cases the
     * subclass is not yet initialized, therefore subclasses overriding this
     * function shouldn't use those constructors.
     */
    protected Socket createSocket() {
        return new Socket();
    }

    /**
     * Returns true if the client is connected to the server.
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sends a message to the server, ie "HELO foo.example.com". A newline will
     * be appended to the message.
     *
     * @param msg
     *            should not have any newlines
     */
    protected void send(String msg) throws IOException {
        log.debug("Client: {}", msg);
        if (!connected)
            throw new IllegalStateException("Not connected");

        // Force \r\n since println() behaves differently on different platforms
        this.writer.print(msg + "\r\n");
        this.writer.flush();
    }

    /**
     * Note that the response text comes back without trailing newlines.
     */
    protected Response receive() throws IOException {
        if (!connected)
            throw new IllegalStateException("Not connected");

        StringBuilder builder = new StringBuilder();
        String line = null;

        boolean done = false;
        do {
            line = this.reader.readLine();
            if (line == null) {
                if (builder.length() == 0)
                    throw new EOFException("Server disconnected unexpectedly, no reply received");
                else
                    throw new IOException("Malformed SMTP reply: " + builder);
            }

            log.debug("Server: {}", line);

            if (line.length() < 4)
                throw new IOException("Malformed SMTP reply: " + line);
            builder.append(line.substring(4));

            if (line.charAt(3) == '-')
                builder.append('\n');
            else
                done = true;
        } while (!done);

        int code;
        String codeString = line.substring(0, 3);
        try {
            code = Integer.parseInt(codeString);
        } catch (NumberFormatException e) {
            throw new IOException("Malformed SMTP reply: " + line, e);
        }

        return new Response(code, builder.toString());
    }

    /**
     * Sends a message to the server, ie "HELO foo.example.com". A newline will
     * be appended to the message.
     *
     * @param msg
     *            should not have any newlines
     * @return the response from the server
     */
    public Response sendReceive(String msg) throws IOException {
        this.send(msg);
        return this.receive();
    }

    /** If response is not success, throw an exception */
    public Response receiveAndCheck() throws IOException, SMTPException {
        Response resp = this.receive();
        if (!resp.isSuccess())
            throw new SMTPException(resp);
        return resp;
    }

    /** If response is not success, throw an exception */
    public Response sendAndCheck(String msg) throws IOException, SMTPException {
        this.send(msg);
        return this.receiveAndCheck();
    }

    /** Logs but otherwise ignores errors */
    public void close() {
        connected = false;

        if (this.socket != null && !this.socket.isClosed()) {
            try {
                this.socket.close();

                log.debug("Closed connection to {}", this.hostPortName);
            } catch (IOException ex) {
                log.error("Problem closing connection to " + this.hostPortName, ex);
            }
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { " + this.hostPortName + "}";
    }

    /**
     * Returns the local socket address.
     */
    public SocketAddress getLocalSocketAddress() {
        return localSocketAddress;
    }

    /**
     * @return a nice pretty description of who we are connected to
     */
    public String getHostPort() {
        return this.hostPortName.orElse(null);
    }

}
