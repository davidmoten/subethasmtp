package org.subethamail.smtp.helper;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.RejectException;

import java.util.List;

public interface BasicMessageListener {

    /**
     * Process a message that has just arrived. If you throw and you want the caller
     * to be given the SMTP error response then throw a {@link RejectException}.
     * 
     * @param from
     *            source of message
     * @param to
     *            destination of message
     * @param data
     *            message content
     * @throws RejectException
     *             when caller to be given an SMTP error response
     */
    void messageArrived(MessageContext context, String from, List<String> to, byte[] data) throws RejectException;

}
