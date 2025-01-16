package org.subethamail.smtp;

import static org.junit.Assert.assertEquals;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.auth.PlainAuthenticationHandlerFactory;
import org.subethamail.smtp.auth.UsernamePasswordValidator;

public class PlainAuthenticationHandlerFactoryTest {

    @Test
    public void testBadBase64String() throws RejectException {
        UsernamePasswordValidator validator = (username, password, c) -> {
            if (!username.equals("fred") || !password.equals("blah")) {
                throw new LoginFailedException();
            }
        };
        AuthenticationHandler auth = new PlainAuthenticationHandlerFactory(validator).create();
        MessageContext context = Mockito.mock(MessageContext.class);
        try {
            auth.auth("AUTH PLAIN b", context);
            Assert.fail();
        } catch (RejectException e) {
            assertEquals("Invalid command argument, not a valid Base64 string", e.getMessage());
        }
    }

}
