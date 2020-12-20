package com.codingchili.excelastic.model;

/**
 * @author Robin Duda
 * <p>
 * Thrown when a parser has not been registered for the given file extension.
 */
public class UnsupportedFileTypeException extends RuntimeException {

    /**
     * @param extension the file extension that was unsupported.
     */
    public UnsupportedFileTypeException(String extension) {
        super(String.format("Missing parser for file extension '%s'.", extension));
    }
}
