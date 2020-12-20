package com.codingchili.excelastic.model;

/**
 * @author Robin Duda
 * <p>
 * Thrown when an invalid filename has been specified.
 */
public class InvalidFileNameException extends RuntimeException {

    /**
     * @param fileName the full filename.
     */
    public InvalidFileNameException(String fileName) {
        super(String.format("File with name '%s' is missing extension.", fileName));
    }
}
