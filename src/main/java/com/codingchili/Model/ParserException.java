package com.codingchili.Model;

/**
 * @author Robin Duda
 *
 * Parser exception to be thrown when an error during xlsx parsing occurs.
 */
public class ParserException extends Throwable {

    public ParserException(Exception e) {
        super(e);
    }

}
