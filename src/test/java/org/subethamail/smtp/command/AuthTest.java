package org.subethamail.smtp.command;

import java.util.Base64;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.auth.EasyAuthenticationHandlerFactory;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.auth.UsernamePasswordValidator;
import org.subethamail.smtp.internal.util.TextUtils;
import org.subethamail.smtp.server.SMTPServer;
import org.subethamail.smtp.util.Client;
import org.subethamail.smtp.util.ServerTestCase;
import org.subethamail.smtp.util.Testing;
import org.subethamail.wiser.Wiser;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * @author Marco Trevisan <mrctrevisan@yahoo.it>
 * @author Jeff Schnitzer
 */
public class AuthTest extends ServerTestCase {
    static final String REQUIRED_USERNAME = "myUserName";
    static final String REQUIRED_PASSWORD = "mySecret01";

    class RequiredUsernamePasswordValidator implements UsernamePasswordValidator {
        @Override
        public void login(String username, String password, MessageContext context) throws LoginFailedException {
            if (!username.equals(REQUIRED_USERNAME) || !password.equals(REQUIRED_PASSWORD)) {
                throw new LoginFailedException();
            }
        }
    }

    public AuthTest(String name) {
        super(name);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.subethamail.smtp.ServerTestCase#setUp()
     */
    @Override
    @SuppressFBWarnings
    protected void setUp() throws Exception {
        UsernamePasswordValidator validator = new RequiredUsernamePasswordValidator();
        EasyAuthenticationHandlerFactory fact = new EasyAuthenticationHandlerFactory(validator);
        this.wiser = Wiser.accepter(Testing.ACCEPTER).server(SMTPServer.port(PORT).authenticationHandlerFactory(fact));
        // this.wiser.setHostname("localhost");
        wiser.start();
        this.c = new Client("localhost", PORT);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.subethamail.smtp.ServerTestCase#tearDown()
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test method for AUTH PLAIN. The sequence under test is as follows:
     * <ol>
     * <li>HELO test</li>
     * <li>User starts AUTH PLAIN</li>
     * <li>User sends username+password</li>
     * <li>We expect login to be successful. Also the Base64 transformations are
     * tested.</li>
     * <li>User issues another AUTH command</li>
     * <li>We expect an error message</li>
     * </ol>
     * {@link org.subethamail.smtp.internal.command.AuthCommand#execute(java.lang.String, org.subethamail.smtp.server.Session)}.
     */
    public void testAuthPlain() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("AUTH PLAIN");
        expect("334");

        String authString = new String(new byte[] { 0 }) + REQUIRED_USERNAME + new String(new byte[] { 0 })
                + REQUIRED_PASSWORD;

        String enc_authString = Base64.getEncoder().encodeToString(TextUtils.getAsciiBytes(authString));
        send(enc_authString);
        expect("235");

        send("AUTH");
        expect("503");
    }

    /**
     * Test method for AUTH LOGIN. The sequence under test is as follows:
     * <ol>
     * <li>HELO test</li>
     * <li>User starts AUTH LOGIN</li>
     * <li>User sends username</li>
     * <li>User cancels authentication by sending "*"</li>
     * <li>User restarts AUTH LOGIN</li>
     * <li>User sends username</li>
     * <li>User sends password</li>
     * <li>We expect login to be successful. Also the Base64 transformations are
     * tested.</li>
     * <li>User issues another AUTH command</li>
     * <li>We expect an error message</li>
     * </ol>
     * {@link org.subethamail.smtp.internal.command.AuthCommand#execute(java.lang.String, org.subethamail.smtp.server.Session)}.
     */
    public void testAuthLogin() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("AUTH LOGIN");
        expect("334");

        String enc_username = Base64.getEncoder().encodeToString(TextUtils.getAsciiBytes(REQUIRED_USERNAME));

        send(enc_username);
        expect("334");

        send("*");
        expect("501");

        send("AUTH LOGIN");
        expect("334");

        send(enc_username);
        expect("334");

        String enc_pwd = Base64.getEncoder().encodeToString(TextUtils.getAsciiBytes(REQUIRED_PASSWORD));
        send(enc_pwd);
        expect("235");

        send("AUTH");
        expect("503");
    }

    public void testMailBeforeAuth() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("MAIL FROM: <john@example.com>");
        expect("250");
    }

    /**
     * Test behaviour of an empty username in login auth.
     */
    public void testEmptyUser() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("AUTH LOGIN");
        expect("334");

        send("");
        expect("334");

        send("");
        expect("535");
    }

    /**
     * Test behaviour of an empty password in login auth.
     */
    public void testEmptyPass() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("AUTH LOGIN");
        expect("334");

        String enc_username = Base64.getEncoder().encodeToString(TextUtils.getAsciiBytes(REQUIRED_USERNAME));

        send(enc_username);
        expect("334");

        send("");
        expect("535");
    }

    /**
     * AUTH LOGIN usernames that are not validly base-64 encoded return 501
     */
    public void testBadUser() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("AUTH LOGIN");
        expect("334");

        send("bad-username");
        expect("501");
    }

    /**
     * AUTH LOGIN usernames that are not validly base-64 encoded return 501 when passed inline
     */
    public void testBadUserInline() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("AUTH LOGIN bad-username");
        expect("501");
    }

    /**
     * Tests that AUTH LOGIN usernames that are not validly base-64 encoded return
     * 501
     */
    public void testBadPass() throws Exception {
        expect("220");

        send("HELO foo.com");
        expect("250");

        send("AUTH LOGIN");
        expect("334");

        String enc_username = Base64.getEncoder().encodeToString(TextUtils.getAsciiBytes(REQUIRED_USERNAME));

        send(enc_username);
        expect("334");

        send("bad-password");
        expect("501");
    }
    
}
