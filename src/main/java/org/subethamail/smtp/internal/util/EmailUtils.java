package org.subethamail.smtp.internal.util;

import com.github.davidmoten.guavamini.Preconditions;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;

/**
 * @author Jeff Schnitzer
 */
public final class EmailUtils {

    private EmailUtils() {
        // prevent instantiation
    }

    /**
     * @return true if the string is a valid email address
     */
    public static boolean isValidEmailAddress(String address) {
        // MAIL FROM: <>
        if (address.length() == 0)
            return true;

        boolean result = false;
        try {
            InternetAddress[] ia = InternetAddress.parse(address, true);
            if (ia.length == 0)
                result = false;
            else
                result = true;
        } catch (AddressException ae) {
            result = false;
        }
        return result;
    }

    /** Looking for an address start, skipping leading spaces */
    private static final int EXTRACT_STATE_SEARCHING = 0;

    /** Looking for an address start in <> brackets, skipping leading spaces */
    private static final int EXTRACT_STATE_OPENING = 1;

    /** Reading an address looking for a space (nobrackets) or last braket close after brackets */
    private static final int EXTRACT_STATE_READING = 2;

    /** Looking for an address close bracket, skipping trailing spaces */
    private static final int EXTRACT_STATE_CLOSING = 3;

    /**
     * Extracts the email address within a <> after a specified offset.
     */
    public static String extractEmailAddress(String args, int offset) {
        int len = args.length();
        StringBuilder builder = new StringBuilder(len - offset);
        int state = EXTRACT_STATE_SEARCHING;
        int brackets = 0;
        int lastValidCharIdx = 0;
        for (int i = offset; i < len; ++i) {
            char ch = args.charAt(i);
            switch (state) {
                case EXTRACT_STATE_SEARCHING:
                    switch (ch) {
                        case ' ':
                            // ignore
                            break;
                        case '<':
                            state = EXTRACT_STATE_OPENING;
                            ++brackets;
                            lastValidCharIdx = builder.length();
                            break;
                        default:
                            state = EXTRACT_STATE_READING;
                            builder.append(ch);
                            lastValidCharIdx = builder.length();
                            break;
                    }
                    break;
                case EXTRACT_STATE_OPENING:
                    // In opening there is always just one <
                    Preconditions.checkArgument(brackets == 1 && builder.length() == 0);
                    switch (ch) {
                        case ' ':
                            /* Ignore opening spaces */
                            break;
                        case '<':
                            state = EXTRACT_STATE_READING;
                            ++brackets;
                            builder.append(ch);
                            lastValidCharIdx = builder.length();
                            break;
                        case '>':
                            return "";
                        default:
                            state = EXTRACT_STATE_CLOSING;
                            builder.append(ch);
                            lastValidCharIdx = builder.length();
                    }
                    break;
                case EXTRACT_STATE_READING:
                    switch (ch) {
                        case ' ':
                            if (brackets > 0) {
                                builder.append(ch);
                                lastValidCharIdx = builder.length();
                            } else {
                                return builder.toString();
                            }
                            break;
                        case '<':
                            ++brackets;
                            builder.append(ch);
                            break;
                        case '>':
                            --brackets;
                            if (brackets == 1) {
                                state = EXTRACT_STATE_CLOSING;
                                builder.append(ch);
                                lastValidCharIdx = builder.length();
                            } else if (brackets > 0) {
                                builder.append(ch);
                                lastValidCharIdx = builder.length();
                            } else if (brackets == 0) {
                                return builder.toString();
                            }
                            /*
                             * If there are a negative numbers of brackets it is an invalid address... address will
                             * expand 'till string end and it will be invalid (as expected)
                             */
                            break;
                        default:
                            builder.append(ch);
                            lastValidCharIdx = builder.length();
                    }
                    break;
                case EXTRACT_STATE_CLOSING:
                    // In closing there is always just one <
                    Preconditions.checkArgument(brackets == 1);
                    switch (ch) {
                        case ' ':
                            /*
                             * Do not signal this space as "valid" we need to keep it only if after there are other non
                             * space characters
                             */
                            builder.append(ch);
                            break;
                        case '<':
                            state = EXTRACT_STATE_READING;
                            ++brackets;
                            builder.append(ch);
                            lastValidCharIdx = builder.length();
                            break;
                        case '>':
                            builder.setLength(lastValidCharIdx);
                            return builder.toString();
                        default:
                            builder.append(ch);
                            lastValidCharIdx = builder.length();
                    }
                    break;
            }
        }

        // Reached input end without address close, returning as address every character read
        return builder.toString();
    }
}
