package org.subethamail.smtp.auth;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;

import org.subethamail.smtp.AuthenticationHandler;
import org.subethamail.smtp.AuthenticationHandlerFactory;
import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.RejectException;

/**
 * Implements the SMTP AUTH PLAIN mechanism.<br>
 * You are only required to plug your UsernamePasswordValidator implementation
 * for username and password validation to take effect.
 *
 * @author Marco Trevisan <mrctrevisan@yahoo.it>
 * @author Jeff Schnitzer
 * @author Ian White <ibwhite@gmail.com>
 */
public final class PlainAuthenticationHandlerFactory implements AuthenticationHandlerFactory
{
    private static final Charset AUTHORIZATION_CHARSET = StandardCharsets.UTF_8;
	private static final List<String> MECHANISMS = new ArrayList<>(1);
	static {
		MECHANISMS.add("PLAIN");
	}

	private final UsernamePasswordValidator helper;

	public PlainAuthenticationHandlerFactory(UsernamePasswordValidator helper)
	{
		this.helper = helper;
	}

	@Override
    public List<String> getAuthenticationMechanisms()
	{
		return MECHANISMS;
	}

	@Override
    public AuthenticationHandler create()
	{
		return new Handler();
	}

	/**
	 */
	class Handler implements AuthenticationHandler
	{
        private String username;
		private String password;

		/* */
		@Override
        public Optional<String> auth(String clientInput, MessageContext context) throws RejectException
		{
			StringTokenizer stk = new StringTokenizer(clientInput);
			String secret = stk.nextToken();
			if (secret.trim().equalsIgnoreCase("AUTH"))
			{
				// Let's read the RFC2554 "initial-response" parameter
				// The line could be in the form of "AUTH PLAIN <base64Secret>"
				if (!stk.nextToken().trim().equalsIgnoreCase("PLAIN"))
				{
					// Mechanism mismatch
					throw new RejectException(504, "AUTH mechanism mismatch");
				}

				if (stk.hasMoreTokens())
				{
					// the client submitted an initial response
					secret = stk.nextToken();
				}
				else
				{
					// the client did not submit an initial response, we'll get it in the next pass
					return Optional.of("334 Ok");
				}
			}

			byte[] decodedSecret;
			try {
			    decodedSecret = Base64.getDecoder().decode(secret);
			} catch (IllegalArgumentException e) {
			    decodedSecret = null;
			}
			
			if (decodedSecret == null) {
				throw new RejectException(501, /*5.5.4*/
						"Invalid command argument, not a valid Base64 string");
			}

			/*
			 * RFC4616: The client presents the authorization identity (identity
			 * to act as), followed by a NUL (U+0000) character, followed by the
			 * authentication identity (identity whose password will be used),
			 * followed by a NUL (U+0000) character, followed by the clear-text
			 * password.
			 */

			int i, j;
			for (i = 0; i < decodedSecret.length && decodedSecret[i] != 0; i++)
				;
			if (i >= decodedSecret.length)
			{
				throw new RejectException(501, /*5.5.4*/
						"Invalid command argument, does not contain NUL");
			}

			for (j = i + 1; j < decodedSecret.length && decodedSecret[j] != 0; j++)
				;
			if (j >= decodedSecret.length)
			{
				throw new RejectException(501, /*5.5.4*/
						"Invalid command argument, does not contain the second NUL");
			}

			//authorizationId not used so commented out
			// String authorizationId = new String(decodedSecret, 0, i, AUTHORIZATION_CHARSET);
			String authenticationId = new String(decodedSecret, i + 1, j - i - 1, AUTHORIZATION_CHARSET);
			String passwd = new String(decodedSecret, j + 1, decodedSecret.length - j - 1, AUTHORIZATION_CHARSET);

			// might be nice to do something with authorizationId, but for
			// purposes of the UsernamePasswordValidator, we just want to use
			// authenticationId

			this.username = authenticationId;
			this.password = passwd;
			try
			{
				PlainAuthenticationHandlerFactory.this.helper.login(this.username.toString(), this.password, context);
			}
			catch (LoginFailedException lfe)
			{
				throw new RejectException(535, /*5.7.8*/
						"Authentication credentials invalid");
			}

			return Optional.empty();
		}

		/* */
		@Override
        public Object getIdentity()
		{
			return this.username;
		}
	}
}
