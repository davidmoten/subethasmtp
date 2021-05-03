package org.subethamail.smtp.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.concurrent.GuardedBy;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.subethamail.smtp.AuthenticationHandlerFactory;
import org.subethamail.smtp.MessageHandlerFactory;
import org.subethamail.smtp.Version;
import org.subethamail.smtp.internal.helper.BasicMessageHandlerFactory;
import org.subethamail.smtp.helper.BasicMessageListener;
import org.subethamail.smtp.helper.SimpleMessageListener;
import org.subethamail.smtp.helper.SimpleMessageListenerAdapter;
import org.subethamail.smtp.internal.server.AcceptAllSessionHandler;
import org.subethamail.smtp.internal.server.CommandHandler;
import org.subethamail.smtp.internal.server.ServerThread;

import com.github.davidmoten.guavamini.Preconditions;
import org.subethamail.smtp.internal.proxy.ProxyHandler;

/**
 * Main SMTPServer class. Construct this object, set the hostName, port, and
 * bind address if you wish to override the defaults, and call start().
 *
 * This class starts a ServerSocket and creates a new instance of the
 * ConnectionHandler class when a new connection comes in. The ConnectionHandler
 * then parses the incoming SMTP stream and hands off the processing to the
 * CommandHandler which will execute the appropriate SMTP command class.
 *
 * To use this class, construct a server with your implementation of the
 * MessageHandlerFactory. This provides low-level callbacks at various phases of
 * the SMTP exchange. For a higher-level but more limited interface, you can
 * pass in a org.subethamail.smtp.helper.SimpleMessageListenerAdapter.
 *
 * By default, no authentication methods are offered. To use authentication, set
 * an {@link AuthenticationHandlerFactory}.
 *
 * @author Jon Stevens
 * @author Ian McFarland &lt;ian@neo.com&gt;
 * @author Jeff Schnitzer
 */
public final class SMTPServer implements SSLSocketCreator {
    private final static Logger log = LoggerFactory.getLogger(SMTPServer.class);

    /** Hostname used if we can't find one */
    private final static String UNKNOWN_HOSTNAME = "localhost";

    private final static int MAX_MESSAGE_SIZE_UNLIMITED = 0;

    private final Optional<InetAddress> bindAddress; // default to all
                                                     // interfaces
    private final int port; // default to 25
    private final String hostName; // defaults to a lookup of the local address
    private final int backlog;
    private final String softwareName;

    private final MessageHandlerFactory messageHandlerFactory;
    private final Optional<AuthenticationHandlerFactory> authenticationHandlerFactory;
    private final ExecutorService executorService;

    private final CommandHandler commandHandler;

    /** If true, TLS is enabled */
    private final boolean enableTLS;

    /** If true, TLS is not announced; ignored if enableTLS=false */
    private final boolean hideTLS;

    /** If true, a TLS handshake is required; ignored if enableTLS=false */
    private final boolean requireTLS;

    /**
     * If true, this server will accept no mail until auth succeeded; ignored if no
     * AuthenticationHandlerFactory has been set
     */
    private final boolean requireAuth;
    
    /**
     * Normally you would not want to advertise AUTH related capabilities before a 
     * STARTTLS is sent so that credentials do not go in the clear. However, the use 
     * of implicit STARTTLS (the property `mail.smtp.starttls.enable` is not set) by
     * a jakarta.mail client (e.g. 1.6.4) prevents AUTH happening. See issue #21. To resolve
     * this issue with jakarta.mail and implicit STARTTLS we provide explicit control 
     * over if AUTH capabilities are shown before STARTTLS. 
     */
    private final boolean showAuthCapabilitiesBeforeSTARTTLS;

    /** If true, no Received headers will be inserted */
    private final boolean disableReceivedHeaders;

    /**
     * set a hard limit on the maximum number of connections this server will accept
     * once we reach this limit, the server will gracefully reject new connections.
     * Default is 1000.
     */
    private final int maxConnections;

    /**
     * The timeout for waiting for data on a connection is one minute: 1000 * 60 * 1
     */
    private final int connectionTimeoutMs;

    /**
     * The maximal number of recipients that this server accepts per message
     * delivery request.
     */
    private final int maxRecipients;

    /**
     * The maximum size of a message that the server will accept. This value is
     * advertised during the EHLO phase if it is larger than 0. If the message size
     * specified by the client during the MAIL phase, the message will be rejected
     * at that time. (RFC 1870) Default is 0. Note this doesn't actually enforce any
     * limits on the message being read; you must do that yourself when reading
     * data.
     */
    private final int maxMessageSize;

    private final SessionIdFactory sessionIdFactory;

    private final SessionHandler sessionHandler;

    private final ProxyHandler proxyHandler;

    // mutable state

    /** The thread listening on the server socket. */
    @GuardedBy("this")
    private ServerThread serverThread;

    private final Function<SMTPServer, String> serverThreadName;

    /**
     * True if this SMTPServer was started. It remains true even if the SMTPServer
     * has been stopped since. It is used to prevent restarting this object. Even if
     * it was shutdown properly, it cannot be restarted, because the contained
     * thread pool object itself cannot be restarted.
     **/
    @GuardedBy("this")
    private boolean started = false;

    private volatile int allocatedPort;

    private final SSLSocketCreator startTlsSocketCreator;

    private final ServerSocketCreator serverSocketCreator;

    public static final class Builder {
        private Optional<String> hostName = Optional.empty();
        private Optional<InetAddress> bindAddress = Optional.empty(); // default
                                                                      // to all
                                                                      // interfaces
        private int port = 25; // default to 25
        private int backlog = 50;
        private String softwareName = "SubEthaSMTP " + Version.getSpecification();

        private Optional<BasicMessageListener> listener = Optional.empty();
        private MessageHandlerFactory messageHandlerFactory = MESSAGE_HANDLER_FACTORY_DEFAULT;

        private Optional<AuthenticationHandlerFactory> authenticationHandlerFactory = Optional.empty();
        private Optional<ExecutorService> executorService = Optional.empty();

        /** If true, TLS is enabled */
        private boolean enableTLS = false;

        /** If true, TLS is not announced; ignored if enableTLS=false */
        private boolean hideTLS = false;

        /** If true, a TLS handshake is required; ignored if enableTLS=false */
        private boolean requireTLS = false;

        /**
         * If true, this server will accept no mail until auth succeeded; ignored if no
         * AuthenticationHandlerFactory has been set
         */
        private boolean requireAuth = false;
        
        /**
         * Normally you would not want to advertise AUTH related capabilities before a 
         * STARTTLS is sent so that credentials do not go in the clear. However, the use 
         * of implicit STARTTLS (the property `mail.smtp.starttls.enable` is not set) by
         * a jakarta.mail client (e.g. 1.6.4) prevents AUTH happening. See issue #21. To resolve
         * this issue with jakarta.mail and implicit STARTTLS we provide explicit control 
         * over if AUTH capabilities are shown before STARTTLS. 
         */
        private boolean showAuthCapabilitiesBeforeSTARTTLS = false;

        /** If true, no Received headers will be inserted */
        private boolean disableReceivedHeaders = false;

        /**
         * set a hard limit on the maximum number of connections this server will accept
         * once we reach this limit, the server will gracefully reject new connections.
         * Default is 1000.
         */
        private int maxConnections = 1000;

        /**
         * The timeout for waiting for data on a connection is one minute: 1000 * 60 * 1
         */
        private int connectionTimeoutMs = 1000 * 60 * 1;

        /**
         * The maximal number of recipients that this server accepts per message
         * delivery request.
         */
        private int maxRecipients = 1000;

        /**
         * The maximum size of a message that the server will accept. This value is
         * advertised during the EHLO phase if it is larger than 0. If the message size
         * specified by the client during the MAIL phase, the message will be rejected
         * at that time. (RFC 1870) Default is 0. Note this doesn't actually enforce any
         * limits on the message being read; you must do that yourself when reading
         * data.
         */
        private int maxMessageSize = MAX_MESSAGE_SIZE_UNLIMITED;

        private SessionIdFactory sessionIdFactory = new TimeBasedSessionIdFactory();

        private SessionHandler sessionHandler = AcceptAllSessionHandler.INSTANCE;

        /* No proxy handling attempt by default */
        private ProxyHandler proxyHandler = ProxyHandler.NOP;

        private SSLSocketCreator startTlsSocketCreator = SSL_SOCKET_CREATOR_DEFAULT;

        private ServerSocketCreator serverSocketCreator = SERVER_SOCKET_CREATOR_DEFAULT;

        private Function<SMTPServer, String> serverThreadNameProvider = server ->
                ServerThread.class.getName() + " " + server.getDisplayableLocalSocketAddress();

        public Builder bindAddress(InetAddress bindAddress) {
            Preconditions.checkNotNull(bindAddress, "bindAddress cannot be null");
            this.bindAddress = Optional.of(bindAddress);
            return this;
        }

        public Builder bindAddress(Optional<InetAddress> bindAddress) {
            Preconditions.checkNotNull(bindAddress, "bindAddress cannot be null");
            this.bindAddress = bindAddress;
            return this;
        }

        public Builder hostName(String hostName) {
            Preconditions.checkNotNull(hostName);
            this.hostName = Optional.of(hostName);
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the Socket backlog which is the requested maximum number of pending
         * connections on the socket.
         *
         * @param backlogSize
         *            The backlog argument must be a >= 0. If the value passed is 0,
         *            then the default value (50) will be assumed.
         *
         * @return this
         */
        public Builder backlog(int backlogSize) {
            Preconditions.checkArgument(backlogSize >= 0);
            this.backlog = backlogSize;
            return this;
        }

        public Builder softwareName(String name) {
            Preconditions.checkNotNull(name);
            this.softwareName = name;
            return this;
        }

        public Builder messageHandler(BasicMessageListener listener) {
            this.listener = Optional.of(listener);
            return this;
        }

        public Builder messageHandlerFactory(MessageHandlerFactory factory) {
            Preconditions.checkNotNull(factory);
            Preconditions.checkArgument(this.messageHandlerFactory == MESSAGE_HANDLER_FACTORY_DEFAULT,
                    "can only set message handler factory once");
            this.messageHandlerFactory = factory;
            return this;
        }

        public Builder simpleMessageListener(SimpleMessageListener listener) {
            this.messageHandlerFactory = new SimpleMessageListenerAdapter(listener);
            return this;
        }

        /**
         * Sets authenticationHandlerFactory.
         * 
         * @param factory
         *            the {@link AuthenticationHandlerFactory} which performs
         *            authentication in the SMTP AUTH command. If empty, authentication
         *            is not supported. Note that setting an authentication handler does
         *            not enforce authentication, it only makes authentication possible.
         *            Enforcing authentication is the responsibility of the client
         *            application, which usually enforces it only selectively. Use
         *            {@link Session#isAuthenticated} to check whether the client was
         *            authenticated in the session.
         * @return this
         */
        public Builder authenticationHandlerFactory(AuthenticationHandlerFactory factory) {
            Preconditions.checkNotNull(factory);
            this.authenticationHandlerFactory = Optional.of(factory);
            return this;
        }

        /**
         * Sets the executor service that will handle client connections.
         * 
         * @param executor
         *            the ExecutorService that will handle client connections, one task
         *            per connection. The SMTPServer will shut down this ExecutorService
         *            when the SMTPServer itself stops. If not specified, a default one
         *            is created by {@link Executors#newCachedThreadPool()}.
         * @return this
         */
        public Builder executorService(ExecutorService executor) {
            Preconditions.checkNotNull(executor);
            this.executorService = Optional.of(executor);
            return this;
        }

        public Builder enableTLS(boolean value) {
            this.enableTLS = value;
            return this;
        }

        /**
         * If set to true, TLS will be supported.
         * <p>
         * The minimal JSSE configuration necessary for a working TLS support on Oracle
         * JRE 6:
         * <ul>
         * <li>javax.net.ssl.keyStore system property must refer to a file containing a
         * JKS keystore with the private key.
         * <li>javax.net.ssl.keyStorePassword system property must specify the keystore
         * password.
         * </ul>
         * <p>
         * Up to SubEthaSMTP 3.1.5 the default was true, i.e. TLS was enabled.
         * 
         * @see <a href=
         *      "http://blog.jteam.nl/2009/11/10/securing-connections-with-tls/">
         *      Securing Connections with TLS</a>
         */
        public Builder enableTLS() {
            return enableTLS(true);
        }

        /**
         * If set to true, TLS will not be advertised in the EHLO string. Default is
         * false; true implied when disableTLS=true.
         */
        public Builder hideTLS(boolean value) {
            this.hideTLS = value;
            return this;
        }

        /**
         * If set to true, TLS will not be advertised in the EHLO string. Default is
         * false; true implied when disableTLS=true.
         */
        public Builder hideTLS() {
            return hideTLS(true);
        }

        /**
         * @param value
         *            true to require a TLS handshake, false to allow operation with or
         *            without TLS. Default is false; ignored when disableTLS=true.
         */
        public Builder requireTLS(boolean value) {
            this.requireTLS = value;
            if (value) {
                enableTLS = true;
            }
            return this;
        }

        public Builder requireTLS() {
            return requireTLS(true);
        }

        /**
         * Sets whether authentication is required. If set to true then no mail will be
         * accepted till authentication succeeds.
         * 
         * @param value
         *            true for mandatory smtp authentication, i.e. no mail mail be
         *            accepted until authentication succeeds. Don't forget to set
         *            {@code authenticationHandlerFactory} to allow client
         *            authentication. Defaults to false.
         */
        public Builder requireAuth(boolean value) {
            this.requireAuth = value;
            return this;
        }

        public Builder requireAuth() {
            return requireAuth(true);
        }
        
        /**
         * Sets whether AUTH capabilities are shown before STARTTLS is received. Default 
         * value is {@code false}.
         * 
         * <p>Normally you would not want to advertise AUTH related capabilities before a 
         * STARTTLS is sent so that credentials do not go in the clear. However, the use 
         * of implicit STARTTLS (the property `mail.smtp.starttls.enable` is not set) by
         * a jakarta.mail client (e.g. 1.6.4) prevents AUTH happening. See issue #21. To resolve
         * this issue with jakarta.mail and implicit STARTTLS we provide explicit control 
         * over if AUTH capabilities are shown before STARTTLS. 
         * 
         * @param value boolean value, default is false
         * @return this
         */
        public Builder showAuthCapabilitiesBeforeSTARTTLS(boolean value) {
            this.showAuthCapabilitiesBeforeSTARTTLS = value;
            return this;
        }

        public Builder insertReceivedHeaders(boolean value) {
            this.disableReceivedHeaders = !value;
            return this;
        }

        public Builder insertReceivedHeaders() {
            this.disableReceivedHeaders = false;
            return this;
        }

        public Builder maxConnections(int maxConnections) {
            this.maxConnections = maxConnections;
            return this;
        }

        public Builder connectionTimeoutMs(int connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout, TimeUnit unit) {
            return connectionTimeoutMs((int) unit.toMillis(connectionTimeout));
        }

        /**
         * Sets the maximum number of recipients per message delivery request.
         * 
         * @param maxRecipients
         *            The maximum number of recipients that this server accepts per
         *            message delivery request.
         * @return this
         */
        public Builder maxRecipients(int maxRecipients) {
            this.maxRecipients = maxRecipients;
            return this;
        }

        /**
         * Sets the maximum messages size (does not enforce though!).
         * 
         * @param maxMessageSize
         *            The maximum size of a message that the server will accept. This
         *            value is advertised during the EHLO phase if it is larger than 0.
         *            If the message size specified by the client during the MAIL phase,
         *            the message will be rejected at that time. (RFC 1870) Default is
         *            0. Note this doesn't actually enforce any limits on the message
         *            being read; you must do that yourself when reading data.
         * @return this
         */
        public Builder maxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        /**
         * Sets the {@link SessionIdFactory} which will allocate a unique identifier for
         * each mail sessions. If not set, a reasonable default will be used.
         */
        public Builder sessionIdFactory(SessionIdFactory factory) {
            this.sessionIdFactory = factory;
            return this;
        }

        /**
         * Sets the {@link SessionHandler} which will track allocated session and connections.
         */
        public Builder sessionHandler(SessionHandler sessionHandler) {
            this.sessionHandler = sessionHandler;
            return this;
        }

        /**
         * Sets the {@link ProxyHandler} which will handle proxy packets before SMTP protocol.
         */
        public Builder proxyHandler(ProxyHandler proxyHandler) {
            this.proxyHandler = proxyHandler;
            return this;
        }

        public Builder serverSocketFactory(ServerSocketCreator serverSocketCreator) {
            this.serverSocketCreator = serverSocketCreator;
            return this;
        }

        public Builder serverSocketFactory(SSLServerSocketFactory factory) {
            return serverSocketFactory(new ServerSocketCreator() {
                @Override
                public ServerSocket createServerSocket() throws IOException {
                    return factory.createServerSocket();
                }
            });
        }

        public Builder serverSocketFactory(SSLContext context) {
            return serverSocketFactory(context.getServerSocketFactory());
        }

        public Builder startTlsSocketFactory(SSLSocketCreator creator) {
            this.startTlsSocketCreator = creator;
            return this;
        }

        public Builder startTlsSocketFactory(SSLContext context) {
            return startTlsSocketFactory(context, false);
        }

        public Builder startTlsSocketFactory(SSLContext context, boolean requireClientCertificate) {
            return startTlsSocketFactory(new SSLSocketCreator() {
                @Override
                public SSLSocket createSSLSocket(Socket socket) throws IOException {
                    InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

                    SSLSocketFactory sf = context.getSocketFactory();
                    SSLSocket s = (SSLSocket) (sf.createSocket(socket, remoteAddress.getHostName(), socket.getPort(),
                            true));

                    // we are a server
                    s.setUseClientMode(false);

                    // select protocols and cipher suites
                    s.setEnabledProtocols(s.getSupportedProtocols());
                    s.setEnabledCipherSuites(s.getSupportedCipherSuites());
                    
                    //// Client must authenticate
                    if (requireClientCertificate) {
                        s.setNeedClientAuth(true);
                    }

                    return s;
                }
            });
        }

        /**
         * Sets the server thead name. The default value is
         *              {@code org.subethamail.smtp.server.ServerThread {bindAddress}:{port}}
         *
         * @param name - thread name
         */
        public Builder serverThreadName(String name){
            Preconditions.checkNotNull(name);
            this.serverThreadNameProvider = server -> name;
            return this;
        }

        public Builder serverThreadNameProvider(Function<SMTPServer, String> provider){
            this.serverThreadNameProvider = provider;
            return this;
        }

        public SMTPServer build() {
            if (listener.isPresent()) {
                messageHandlerFactory(new BasicMessageHandlerFactory(listener.get(), maxMessageSize));
            }
            return new SMTPServer(hostName, bindAddress, port, backlog, softwareName, messageHandlerFactory,
                    authenticationHandlerFactory, executorService, enableTLS, hideTLS, requireTLS, requireAuth,
                    showAuthCapabilitiesBeforeSTARTTLS, disableReceivedHeaders, maxConnections, connectionTimeoutMs,
                    maxRecipients, maxMessageSize, sessionIdFactory, sessionHandler, proxyHandler, startTlsSocketCreator,
                    serverSocketCreator, serverThreadNameProvider);
        }

    }

    private SMTPServer(Optional<String> hostName, Optional<InetAddress> bindAddress, int port, int backlog,
            String softwareName, MessageHandlerFactory messageHandlerFactory,
            Optional<AuthenticationHandlerFactory> authenticationHandlerFactory,
            Optional<ExecutorService> executorService, boolean enableTLS, boolean hideTLS, boolean requireTLS,
            boolean requireAuth, boolean showAuthCapabilitiesBeforeSTARTTLS, boolean disableReceivedHeaders,
            int maxConnections, int connectionTimeoutMs, int maxRecipients, int maxMessageSize,
            SessionIdFactory sessionIdFactory, SessionHandler sessionHandler, ProxyHandler proxyHandler,
            SSLSocketCreator startTlsSocketFactory, ServerSocketCreator serverSocketCreator,
            Function<SMTPServer, String> serverThreadNameProvider) {
        Preconditions.checkNotNull(messageHandlerFactory);
        Preconditions.checkNotNull(bindAddress);
        Preconditions.checkNotNull(executorService);
        Preconditions.checkNotNull(authenticationHandlerFactory);
        Preconditions.checkNotNull(sessionIdFactory);
        Preconditions.checkNotNull(sessionHandler);
        Preconditions.checkNotNull(hostName);
        Preconditions.checkNotNull(serverThreadNameProvider);
        Preconditions.checkArgument(!requireAuth || authenticationHandlerFactory.isPresent(),
                "if requireAuth is set to true then you must specify an authenticationHandlerFactory");
        Preconditions.checkNotNull(startTlsSocketFactory, "startTlsSocketFactory cannot be null");
        this.bindAddress = bindAddress;
        this.port = port;
        this.backlog = backlog;
        this.softwareName = softwareName;
        this.messageHandlerFactory = messageHandlerFactory;
        this.authenticationHandlerFactory = authenticationHandlerFactory;
        this.enableTLS = enableTLS;
        this.hideTLS = hideTLS;
        this.requireTLS = requireTLS;
        this.requireAuth = requireAuth;
        this.showAuthCapabilitiesBeforeSTARTTLS = showAuthCapabilitiesBeforeSTARTTLS;
        this.disableReceivedHeaders = disableReceivedHeaders;
        this.maxConnections = maxConnections;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.maxRecipients = maxRecipients;
        this.maxMessageSize = maxMessageSize;
        this.sessionIdFactory = sessionIdFactory;
        this.sessionHandler = sessionHandler;
        this.proxyHandler = proxyHandler;
        this.commandHandler = new CommandHandler();
        this.serverSocketCreator = serverSocketCreator;
        this.startTlsSocketCreator = startTlsSocketFactory;

        if (executorService.isPresent()) {
            this.executorService = executorService.get();
        } else {
            this.executorService = Executors.newCachedThreadPool();
        }
        if (!hostName.isPresent()) {
            String s;
            try {
                s = InetAddress.getLocalHost().getCanonicalHostName();
            } catch (UnknownHostException e) {
                s = UNKNOWN_HOSTNAME;
            }
            this.hostName = s;
        } else {
            this.hostName = hostName.get();
        }
        this.allocatedPort = port;
        this.serverThreadName = serverThreadNameProvider;
    }

    private static final SSLSocketCreator SSL_SOCKET_CREATOR_DEFAULT = new SSLSocketCreator() {

        @Override
        public SSLSocket createSSLSocket(Socket socket) throws IOException {
            SSLSocketFactory sf = ((SSLSocketFactory) SSLSocketFactory.getDefault());
            InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();
            SSLSocket s = (SSLSocket) (sf.createSocket(socket, remoteAddress.getHostName(), socket.getPort(), true));

            // we are a server
            s.setUseClientMode(false);

            // allow all supported cipher suites
            s.setEnabledCipherSuites(s.getSupportedCipherSuites());

            return s;
        }
    };

    private static final ServerSocketCreator SERVER_SOCKET_CREATOR_DEFAULT = new ServerSocketCreator() {
        @Override
        public ServerSocket createServerSocket() throws IOException {
            return new ServerSocket();
        }
    };

    private static final MessageHandlerFactory MESSAGE_HANDLER_FACTORY_DEFAULT = new BasicMessageHandlerFactory(
            (context, from, to, data) -> log.info("From: " + from + ", To: " + to + "\n"
                    + new String(data, StandardCharsets.UTF_8) + "\n--------END OF MESSAGE ------------"),
            MAX_MESSAGE_SIZE_UNLIMITED);

    /** @return the host name that will be reported to SMTP clients */
    public String getHostName() {
        return this.hostName;
    }

    /** empty means all interfaces */
    public Optional<InetAddress> getBindAddress() {
        return this.bindAddress;
    }

    public int getPort() {
        return this.port;
    }

    public int getPortAllocated() {
        return this.allocatedPort;
    }

    /**
     * The string reported to the public as the software running here. Defaults to
     * SubEthaSTP and the version number.
     */
    public String getSoftwareName() {
        return this.softwareName;
    }

    /**
     * @return the ExecutorService handling client connections
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Is the server running after start() has been called?
     */
    public synchronized boolean isRunning() {
        return this.serverThread != null;
    }

    /**
     * The backlog is the Socket backlog.
     *
     * The backlog argument must be a positive value greater than 0. If the value
     * passed if equal or less than 0, then the default value will be assumed.
     *
     * @return the backlog
     */
    public int getBacklog() {
        return this.backlog;
    }

    /**
     * Starts the server listening for connections. When this method returns the
     * server socket will have been established and will accept connections though
     * the thread that processes accepted connections (queued) may not have started
     * yet (it runs asynchronously).
     * <p>
     * An SMTPServer which has been shut down, must not be reused.
     */
    public synchronized void start() {
        log.info("SMTP server {} starting", getDisplayableLocalSocketAddress());

        if (this.started)
            throw new IllegalStateException("SMTPServer can only be started once. "
                    + "Restarting is not allowed even after a proper shutdown.");

        // Create our server socket here.
        ServerSocket serverSocket;
        try {
            serverSocket = this.createServerSocket();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.serverThread = new ServerThread(this, serverSocket, proxyHandler);
        this.serverThread.start();
        this.started = true;
    }

    /**
     * Shut things down gracefully.
     */
    public synchronized void stop() {
        log.info("SMTP server {} stopping...", getDisplayableLocalSocketAddress());
        if (this.serverThread == null)
            return;

        this.serverThread.shutdown();
        this.serverThread = null;

        log.info("SMTP server {} stopped", getDisplayableLocalSocketAddress());
    }

    private ServerSocket createServerSocket() throws IOException {
        InetSocketAddress isa;

        if (!this.bindAddress.isPresent()) {
            isa = new InetSocketAddress(this.port);
        } else {
            isa = new InetSocketAddress(this.bindAddress.orElse(null), this.port);
        }

        ServerSocket serverSocket = serverSocketCreator.createServerSocket();
        serverSocket.bind(isa, backlog);
        if (this.port == 0) {
            this.allocatedPort = serverSocket.getLocalPort();
        }

        return serverSocket;
    }

    /**
     * Create an SSL socket that wraps the existing socket. This method is called
     * after the client issued the STARTTLS command.
     * <p>
     * Subclasses may override this method to configure the key stores, enabled
     * protocols/ cipher suites, enforce client authentication, etc.
     *
     * @param socket
     *            the existing socket as created by {@link #createServerSocket()}
     *            (not null)
     * @return an SSLSocket
     * @throws IOException
     *             when creating the socket failed
     */
    @Override
    public final SSLSocket createSSLSocket(Socket socket) throws IOException {
        return startTlsSocketCreator.createSSLSocket(socket);
    }

    public String getDisplayableLocalSocketAddress() {
        return this.bindAddress.map(x -> x.toString()).orElse("*") + ":" + this.port;
    }

    public MessageHandlerFactory getMessageHandlerFactory() {
        return this.messageHandlerFactory;
    }

    /**
     * Returns the factor for authentication handling.
     * 
     * @return the factory for auth handlers, or empty if no factory has been set.
     */
    public Optional<AuthenticationHandlerFactory> getAuthenticationHandlerFactory() {
        return this.authenticationHandlerFactory;
    }

    /**
     * The CommandHandler manages handling the SMTP commands such as QUIT, MAIL,
     * RCPT, DATA, etc.
     *
     * @return An instance of CommandHandler
     */
    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    public int getMaxConnections() {
        return this.maxConnections;
    }

    public int getConnectionTimeout() {
        return this.connectionTimeoutMs;
    }

    public int getMaxRecipients() {
        return this.maxRecipients;
    }

    public boolean getEnableTLS() {
        return enableTLS;
    }

    public boolean getHideTLS() {
        return this.hideTLS;
    }

    public boolean getRequireTLS() {
        return this.requireTLS;
    }

    public boolean getRequireAuth() {
        return requireAuth;
    }
    
    public boolean getShowAuthCapabilitiesBeforeSTARTTLS() {
        return showAuthCapabilitiesBeforeSTARTTLS;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public boolean getDisableReceivedHeaders() {
        return disableReceivedHeaders;
    }

    public SessionIdFactory getSessionIdFactory() {
        return sessionIdFactory;
    }

    public SessionHandler getSessionHandler() {
        return sessionHandler;
    }

    public static Builder port(int port) {
        return new Builder().port(port);
    }

    public String getServerThreadName() {
        return this.serverThreadName.apply(this);
    }

}
