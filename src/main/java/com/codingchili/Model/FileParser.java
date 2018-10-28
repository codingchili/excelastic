package com.codingchili.Model;

import io.vertx.core.json.JsonObject;
import org.reactivestreams.Publisher;

import java.io.FileNotFoundException;
import java.util.Set;

/**
 * @author Robin Duda
 * <p>
 * Interface used to support different input file formats.
 * The parser is subscribable and emits json objects for importing.
 */
public interface FileParser extends Publisher<JsonObject> {

    /**
     * @param localFileName a file on disk to be parsed, do not read this into memory
     *                 as it could be potentially very large.
     * @param offset   indicates how many empty rows to skip before finding the titles.
     * @param fileName the original name of the file to be imported.
     */
    void setFileData(String localFileName, int offset, String fileName) throws FileNotFoundException;

    /**
     * @return a set of file extensions that this fileparser supports.
     */
    Set<String> getSupportedFileExtensions();

    /**
     * Parses the excel file to make sure that it is parseable without allocating memory
     * for the result. This should be called before importing to make
     * sure any imports does not fail halfway through.
     */
    void initialize();

    /**
     * @return the number of elements that was parsed.
     */
    int getNumberOfElements();


    /**
     * Releases any resources associated with the FileParser.
     */
    void free();
}
