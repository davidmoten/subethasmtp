package org.subethamail.smtp.internal.util;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Iterator;

import com.github.davidmoten.guavamini.annotations.VisibleForTesting;

/**
 * @author Jeff Schnitzer
 */
public final class TextUtils {

    private TextUtils() {
        // prevent instantiation
    }

    /**
     * @return a delimited string containing the specified items
     */
    public static String joinTogether(Collection<String> items, String delim) {
        StringBuilder ret = new StringBuilder();

        for (Iterator<String> it = items.iterator(); it.hasNext();) {
            ret.append(it.next());
            if (it.hasNext()) {
                ret.append(delim);
            }
        }

        return ret.toString();
    }

    /**
     * @return the value of str.getBytes() without the idiotic checked exception
     */
    public static byte[] getBytes(String str, String charset) {
        try {
            return str.getBytes(charset);
        } catch (UnsupportedEncodingException ex) {
            throw new IllegalArgumentException(charset, ex);
        }
    }

    /** @return the string as US-ASCII bytes */
    public static byte[] getAsciiBytes(String str) {
        return getBytes(str, "US-ASCII");
    }

    /**
     * Converts the specified byte array to a string using the specified
     * encoding but without throwing a checked exception. This is useful if the
     * specified encoding is required to be available by the JRE specification,
     * so the exception would be guaranteed to be not thrown anyway.
     */
    @VisibleForTesting
    static String getString(byte[] bytes, String charset) {
        try {
            return new String(bytes, charset);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Converts the specified bytes to String using UTF-8 encoding.
     */
    public static String getStringUtf8(byte[] bytes) {
        return getString(bytes, "UTF-8");
    }
}
