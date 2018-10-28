package com.codingchili.Model;

import io.vertx.core.MultiMap;

import java.util.Arrays;
import java.util.Optional;

import static com.codingchili.Controller.Website.UPLOAD_ID;
import static com.codingchili.Model.ExcelParser.INDEX;

/**
 * @author Robin Duda
 * <p>
 * Contains infromation about an import request.
 */
public class ImportEvent {
    private static final String ARG_CLEAR = "--clear";
    private static final String ARG_OFFSET = "--offset";
    private static final String ARG_MAPPING = "--mapping";
    private static final String OFFSET = "offset";
    private static final String MAPPING = "mapping";
    private static final String OPTIONS = "options";
    private static final String CLEAR = "clear";
    private FileParser parser;
    private Boolean clearExisting;
    private String mapping;
    private String index;
    private String uploadId;
    private int offset;

    /**
     * Creates a new import event from a MultiMap from an HTTP upload request.
     *
     * @param params contains request params with information on which index to use etc.
     * @return the created import event.
     */
    public static ImportEvent fromParams(MultiMap params) {
        return new ImportEvent()
                .setIndex(params.get(INDEX))
                .setMapping(getMappingByParams(params))
                .setClearExisting(params.get(OPTIONS).equals(CLEAR))
                .setUploadId(params.get(UPLOAD_ID))
                .setOffset(Integer.parseInt(params.get(OFFSET)));
    }

    /**
     * Creates a new import event from command line options.
     *
     * @param args the commandline args to create the event from/
     * @return the created import event.
     */
    public static ImportEvent fromCommandLineArgs(String[] args) {
        return new ImportEvent()
                .setIndex(args[1])
                .setOffset(getArgParamValue(args, ARG_OFFSET).map(Integer::parseInt).orElse(1))
                .setClearExisting(Arrays.asList(args).contains(ARG_CLEAR))
                .setMapping(getArgParamValue(args, ARG_MAPPING).orElse("default"));
    }

    private static Optional<String> getArgParamValue(String[] args, String argName) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(argName)) {
                if (i + 1 < args.length) {
                    return Optional.of(args[i + i]);
                }
            }
        }
        return Optional.empty();
    }

    private static String getMappingByParams(MultiMap params) {
        return (params.get(MAPPING).length() == 0) ? "default" : params.get(MAPPING);
    }

    /**
     * @return the id of this file parser if set.
     */
    public String getUploadId() {
        return uploadId;
    }

    /**
     * @param uploadId the id to set for this file parser that can be used for sending progress notifications.
     * @return fluent.
     */
    public ImportEvent setUploadId(String uploadId) {
        this.uploadId = uploadId;
        return this;
    }

    public int getOffset() {
        return offset;
    }

    public ImportEvent setOffset(int offset) {
        this.offset = offset;
        return this;
    }

    public FileParser getParser() {
        return parser;
    }

    public ImportEvent setParser(FileParser parser) {
        this.parser = parser;
        return this;
    }

    public Boolean getClearExisting() {
        return clearExisting;
    }

    public ImportEvent setClearExisting(Boolean clearExisting) {
        this.clearExisting = clearExisting;
        return this;
    }

    public String getMapping() {
        return mapping;
    }

    public ImportEvent setMapping(String mapping) {
        this.mapping = mapping;
        return this;
    }

    public String getIndex() {
        return index;
    }

    public ImportEvent setIndex(String index) {
        this.index = index;
        return this;
    }
}
