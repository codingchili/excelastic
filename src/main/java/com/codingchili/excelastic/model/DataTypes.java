package com.codingchili.excelastic.model;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Attempts to determine the data type based on the given value.
 * <p>
 * The supported data types are,
 * Numbers - formatted into a Long.
 * Floating points - formatted into a double.
 * Booleans - parsed into a Boolean.
 */
public class DataTypes {
    private static final Predicate<String> floatPattern = Pattern.compile("^[0-9]+\\.[0-9]+$").asPredicate();
    private static final Predicate<String> numberPattern = Pattern.compile("^[0-9]+$").asPredicate();
    private static final Predicate<String> boolPattern = Pattern.compile("^(true|false)$").asPredicate();

    /**
     * @param data a byte array to identify the type of and format into its matching java-type.
     * @return the given value parsed as a supported java type, defaults to string.
     */
    public static Object parseBytes(byte[] data) {
        return parseString(new String(data).trim());
    }

    /**
     * @param value used to identify the type, and format into its matching java-type.
     * @return the given value parsed as a supported java type.
     */
    public static Object parseString(String value) {
        if (value.length() > 0) {
            if (numberPattern.test(value)) {
                return Long.parseLong(value);
            } else if (floatPattern.test(value)) {
                return Double.parseDouble(value);
            } else if (boolPattern.test(value)) {
                return Boolean.parseBoolean(value);
            } else {
                return value;
            }
        } else {
            return value;
        }
    }
}
