package com.codingchili.Model;

/**
 * @author Robin Duda
 *
 * Thrown when more columns are encountered than there is headers.
 */
public class ColumnsHeadersMismatchException extends ParserException {

    /**
     * @param values number of values encountered
     * @param headers the number of headers on the first row.
     * @param row the line in the file.
     */
    public ColumnsHeadersMismatchException(int values, int headers, long row) {
        super(String.format("Encountered values (%d) on row %d, expected to match headers (%d).",
                values, row, headers));
    }
}
