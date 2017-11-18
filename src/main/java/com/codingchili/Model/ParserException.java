package com.codingchili.Model;

/**
 * @author Robin Duda
 *
 * Parser exception to be thrown when an error during xlsx parsing occurs.
 */
public class ParserException extends RuntimeException {

    public ParserException(Throwable e) {
        super(e);
    }

    public ParserException(String message) {
        super(message);
    }
}
