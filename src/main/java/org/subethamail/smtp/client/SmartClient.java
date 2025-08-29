package org.subethamail.smtp.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.client.SMTPClient.Response;

import com.github.davidmoten.guavamini.Preconditions;
import com.github.davidmoten.guavamini.annotations.VisibleForTesting;

import javax.net.ssl.SSLSocketFactory;

/**
 * A somewhat smarter abstraction of an SMTP client which doesn't require
 * knowing anything about the nitty gritty of SMTP.
 *
 * @author Jeff Schnitzer
 */
// not final so can mock with Mockito
public class SmartClient {

    private static final Logger log = LoggerFactory.getLogger(SmartClient.class);
    
    /** The host name which is sent in the HELO and EHLO commands */
    private final String heloHost;
    
    /**
     * SMTP extensions supported by the server, and their parameters as the
     * server specified it in response to the EHLO command. Key is the extension
     * keyword in upper case, like "AUTH", value is the extension parameters
     * string in unparsed form. If the server does not support EHLO, then this
     * map is empty.
     */
    private final Map<String, String> extensions = new HashMap<>();

    /**
     * If supplied (not null), then it will be called after EHLO, to
     * authenticate this client to the server.
     */
    private final Optional<Authenticator> authenticator;

    private final SMTPClient client;

    //mutable state
    
    private int recipientCount;

    /**
     * True if the server sent a 421
     * "Service not available, closing transmission channel" response. In this
     * case the QUIT command should not be sent.
     */
    private boolean serverClosingTransmissionChannel = false;

    /**
     * Constructor.
     * 
     * @param bindpoint
     * @param clientHeloHost
     * @param authenticator
     *            the Authenticator object which will be called after the EHLO
     *            command to authenticate this client to the server. If is null
     *            then no authentication will happen.
     * @param sslSocketFactory
     *            the SSLSocketFactory used to create a new SSLSocket when
     *            startTLS() is called on the client.  If empty, the default
     *            SSLSocketFactory will be used.
     */
    private SmartClient(Optional<SocketAddress> bindpoint, String clientHeloHost, Optional<Authenticator> authenticator, Optional<SSLSocketFactory> sslSocketFactory) {
        this(new SMTPClient(Preconditions.checkNotNull(bindpoint, "bindpoint cannot be null"), Optional.empty(), sslSocketFactory),
                clientHeloHost, authenticator);
    }

    /**
     * Constructor.
     * 
     * @param client
     * @param clientHeloHost
     * @param authenticator
     *            the Authenticator object which will be called after the EHLO
     *            command to authenticate this client to the server. If is null
     *            then no authentication will happen.
     */
    @VisibleForTesting
    protected SmartClient(SMTPClient client, String clientHeloHost, Optional<Authenticator> authenticator) {
        Preconditions.checkNotNull(client, "client cannot be null");
        Preconditions.checkNotNull(clientHeloHost, "clientHeloHost cannot be null");
        Preconditions.checkNotNull(authenticator, "authenticator cannot be null");
        this.client = client;
        this.heloHost = clientHeloHost;
        this.authenticator = authenticator;
    }

    public final static SmartClient createAndConnect(String host, int port, String clientHeloHost)
            throws UnknownHostException, SMTPException, IOException {
        return createAndConnect(host, port, Optional.empty(), clientHeloHost, Optional.empty());
    }

    public final static SmartClient createAndConnect(String host, int port, Optional<SocketAddress> bindpoint,
            String clientHeloHost, Optional<Authenticator> authenticator)
                    throws UnknownHostException, SMTPException, IOException {
        return createAndConnect(host, port, bindpoint, clientHeloHost, authenticator, Optional.empty());
    }

    public final static SmartClient createAndConnect(String host, int port, Optional<SocketAddress> bindpoint, String clientHeloHost, Optional<Authenticator> authenticator, Optional<SSLSocketFactory> sslSocketFactory) throws UnknownHostException, SMTPException, IOException {
        SmartClient client = new SmartClient(bindpoint, clientHeloHost, authenticator, sslSocketFactory);
        client.connect(host, port);
        return client;
    }

    /**
     * Connects to the specified server and issues the initial HELO command. It
     * gracefully closes the connection if it could be established but
     * subsequently it fails or if the server does not accept messages.
     */
    public void connect(String host, int port)
            throws SMTPException, AuthenticationNotSupportedException, IOException {
        client.connect(host, port);
        try {
            client.receiveAndCheck(); // The server announces itself first
            this.sendHeloOrEhlo();
            if (this.authenticator.isPresent())
                this.authenticator.get().authenticate();
        } catch (SMTPException | AuthenticationNotSupportedException e) {
            this.quit();
            throw e;
        } catch (IOException e) {
            client.close(); // just close the socket, issuing QUIT is hopeless
                            // now
            throw e;
        }
    }

    /**
     * Sends the EHLO command, or HELO if EHLO is not supported, and saves the
     * list of SMTP extensions which are supported by the server.
     */
    protected void sendHeloOrEhlo() throws IOException, SMTPException {
        extensions.clear();
        Response resp = sendEhlo();
        if (!resp.isSuccess()) {
            if (resp.getCode() == 500 || resp.getCode() == 502) {
                // server does not support EHLO, try HELO
                client.sendAndCheck("HELO " + heloHost);
            } else {
                // some serious error
                throw new SMTPException(resp);
            }
        }
    }

    /**
     * Sends the EHLO command, saving the list of SMTP extensions supported by
     * the server on success.
     *
     * @return The EHLO response.
     * @throws IOException
     */
    protected Response sendEhlo() throws IOException {
        Response resp = client.sendReceive("EHLO " + heloHost);
        if (resp.isSuccess()) {
            parseEhloResponse(resp);
        }

        return resp;
    }

    /**
     * Extracts the list of SMTP extensions from the server's response to EHLO,
     * and stores them in {@link #extensions}.
     */
    private void parseEhloResponse(Response resp) throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader(resp.getMessage()));
        // first line contains server name and welcome message, skip it
        reader.readLine();
        String line;
        while (null != (line = reader.readLine())) {
            int iFirstSpace = line.indexOf(' ');
            String keyword = iFirstSpace == -1 ? line : line.substring(0, iFirstSpace);
            String parameters = iFirstSpace == -1 ? "" : line.substring(iFirstSpace + 1);
            extensions.put(keyword.toUpperCase(Locale.ENGLISH), parameters);
        }
    }

    /**
     * Returns the server response. It takes note of a 421 response code, so
     * QUIT will not be issued unnecessarily.
     */
    protected Response receive() throws IOException {
        Response response = client.receive();
        if (response.getCode() == 421)
            serverClosingTransmissionChannel = true;
        return response;
    }

    /**
     * Upgrades the connection to use TLS with the STARTTLS command and reissues the EHLO to the server.
     *
     * @throws IllegalStateException Thrown if a server did not indicate it supports STARTTLS in the original EHLO/HELO.
     * @throws IOException
     * @throws SMTPException
     */
    public void startTLS() throws IOException, SMTPException {
        if(!extensions.containsKey("STARTTLS")){
            throw new IllegalStateException("called STARTTLS on a server that did not advertise STARTTLS support in the EHLO/HELO response");
        }
        client.sendAndCheck("STARTTLS");

        client.performSSLHandshake();
        extensions.clear();
        Response resp = sendEhlo();
        if (!resp.isSuccess()) {
            throw new SMTPException(resp);
        }
    }


    public void from(String from) throws IOException, SMTPException {
        client.sendAndCheck("MAIL FROM: <" + from + ">");
    }

    public void to(String to) throws IOException, SMTPException {
        client.sendAndCheck("RCPT TO: <" + to + ">");
        this.recipientCount++;
    }

    /**
     * Prelude to writing data
     */
    public void dataStart() throws IOException, SMTPException {
        client.sendAndCheck("DATA");
    }
    
    public void bdat(String text, boolean isLast) throws IOException {
        client.send("BDAT " + text.length() + (isLast? " LAST": ""));
        dataWrite(text.getBytes(StandardCharsets.UTF_8));
        client.dataOutput.flush();
        log.debug("receiving bdat response");
        client.receiveAndCheck();
        log.debug("received bdat response");
    }
    
    public void bdat(String text) throws IOException {
        bdat(text, false);
    }
    
    public void bdatLast(String text) throws IOException {
        bdat(text, true);
    }

    /**
     * Actually write some data
     */
    public void dataWrite(byte[] data, int numBytes) throws IOException {
        client.dataOutput.write(data, 0, numBytes);
    }
    
    public void dataWrite(byte[] data) throws IOException {
        client.dataOutput.write(data, 0, data.length);
    }

    /**
     * Last step after writing data
     */
    public void dataEnd() throws IOException, SMTPException {
        client.dataOutput.flush();
        client.dotTerminatedOutput.writeTerminatingSequence();
        client.dotTerminatedOutput.flush();

        client.receiveAndCheck();
    }

    /**
     * Quit and close down the connection. Ignore any errors.
     * <p>
     * It still closes the connection, but it does not send the QUIT command if
     * a 421 Service closing transmission channel is received previously. In
     * these cases QUIT would fail anyway.
     * 
     * @see <a href="http://tools.ietf.org/html/rfc5321#section-3.8">RFC 5321
     *      Terminating Sessions and Connections</a>
     */
    public void quit() {
        try {
            if (client.isConnected() && !this.serverClosingTransmissionChannel)
                client.sendAndCheck("QUIT");
        } catch (IOException ex) {
            log.warn("Failed to issue QUIT to " + client.getHostPort());
        }

        client.close();
    }

    /**
     * @return the number of recipients that have been accepted by the server
     */
    public int getRecipientCount() {
        return this.recipientCount;
    }

    /**
     * Returns the SMTP extensions supported by the server.
     * 
     * @return the extension map. Key is the extension keyword in upper case,
     *         value is the unparsed string of extension parameters.
     */
    public Map<String, String> getExtensions() {
        return extensions;
    }

    /**
     * Returns the HELO name of this system.
     */
    public String getHeloHost() {
        return heloHost;
    }

    /**
     * Returns the Authenticator object, which is used to authenticate this
     * client to the server, or null, if no authentication is required.
     */
    public Optional<Authenticator> getAuthenticator() {
        return authenticator;
    }

    public void sendAndCheck(String msg) throws SMTPException, IOException {
        client.sendAndCheck(msg);
    }
}
