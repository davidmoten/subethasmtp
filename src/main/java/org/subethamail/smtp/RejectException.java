/*
 * $Id$
 * $Source: /cvsroot/Similarity4/src/java/com/similarity/mbean/BindStatisticsManagerMBean.java,v $
 */
package org.subethamail.smtp;

/**
 * Thrown to reject an SMTP command with a specific code.
 *
 * @author Jeff Schnitzer
 */
@SuppressWarnings("serial")
public class RejectException extends Exception
{
	public static final int DEFAULT_CODE = 554;
	public static final String DEFAULT_MESSAGE = "Transaction failed";

	final int code;

	public RejectException()
	{
		this(DEFAULT_MESSAGE);
	}

	public RejectException(String message)
	{
		this(DEFAULT_CODE, message);
	}

	public RejectException(int code, String message)
	{
		this(code, message, null);
	}

	public RejectException(int code, String message, Throwable cause)
	{
		super(message, cause, true, false);

		this.code = code;
	}

	public int getCode()
	{
		return this.code;
	}

	public String getErrorResponse()
	{
		return this.code + " " + this.getMessage();
	}
}
