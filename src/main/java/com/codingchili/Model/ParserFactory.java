package com.codingchili.Model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * @author Robin Duda
 * <p>
 * Handles support for multiple file formats.
 */
public class ParserFactory {
    private static final Map<String, Supplier<FileParser>> parsers = new ConcurrentHashMap<>();

    static {
        register(ExcelParser::new);
        register(CSVParser::new);
    }

    /**
     * @param parser the given parser is instantiated and registered for use with its
     *               supported file extensions.
     */
    public static void register(Supplier<FileParser> parser) {
        for (String ext : parser.get().getSupportedFileExtensions()) {
            parsers.put(ext, parser);
        }
    }

    /**
     * Retrieves a file parser that is registered for the file extension in the given filename.
     *
     * @param fileName a filename that contains an extension.
     * @return a parser that is registered for use with the given extension, throws an
     * exception if no parser exists or if the file does not have an extension.
     */
    public static FileParser getByFilename(String fileName) {
        int extensionAt = fileName.lastIndexOf(".");

        if (extensionAt > 0) {
            // include the dot separator in the extension.
            String extension = fileName.substring(extensionAt);

            if (parsers.containsKey(extension)) {
                return parsers.get(extension).get();
            } else {
                throw new UnsupportedFileTypeException(extension);
            }
        } else {
            throw new InvalidFileNameException(fileName);
        }
    }

    /**
     * @return a list of file extensions that is registered in the parser factory.
     */
    public static Set<String> getSupportedExtensions() {
        return parsers.keySet();
    }
}
