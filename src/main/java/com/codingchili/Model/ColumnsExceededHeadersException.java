package com.codingchili.Model;

/**
 * @author Robin Duda
 *
 * Thrown when more columns are encountered than there is headers.
 */
public class ColumnsExceededHeadersException extends ParserException {

    /**
     * @param values number of values encountered
     * @param headers the number of headers on the first row.
     * @param row the line in the file.
     */
    public ColumnsExceededHeadersException(int values, int headers, long row) {
        super(String.format("Encountered too many values (%d) on row %d, expected to match headers (%d).",
                values, row, headers));
    }
}
