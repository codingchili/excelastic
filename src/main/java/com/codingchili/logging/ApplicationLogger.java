package com.codingchili.logging;

import com.codingchili.Model.*;
import io.vertx.core.http.HttpClientResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.codingchili.ApplicationLauncher.VERSION;
import static com.codingchili.Model.ElasticWriter.MAX_BATCH;

/**
 * @author Robin Duda
 * <p>
 * Provides logging utility methods, avoids littering the code with log statements.
 */
public class ApplicationLogger {
    private Logger logger;

    /**
     * @param sourceClass the source class that will be the emitter when any logging methods are called.
     */
    public ApplicationLogger(Class sourceClass) {
        this.logger = Logger.getLogger(sourceClass.getSimpleName());
    }

    /**
     * Prints the startup message, this should include some helpful information on how
     * to use the available commandline options.
     */
    public void startupMessage() {
        logger.info(String.format("Starting excelastic %s..", VERSION));
        logger.info("to import files without the web interface use please supply arguments for <fileName> <indexName>");
        logger.info("optional arguments: --mapping <mappingName> --offset <number> --clear");
    }

    public void applicationStartup() {
        logger.info("Successfully started application");
    }

    /**
     * Called when opening the browser.
     *
     * @param connectedState a string representing the writers connected state when opening the browser.
     */
    public void openingBrowser(String connectedState) {
        logger.info(String.format("Attempting to open browser.. [ES connected=%s]", connectedState));
    }

    /**
     * Can be called with any exception.
     *
     * @param e the exception that caused the error, the stack trace of this error will be logged.
     */
    public void onError(Throwable e) {
        logger.severe(traceToText(e));
    }

    /**
     * Called when the application has failed to start correctly.
     *
     * @param cause the cause of the startup failure.
     */
    public void applicationStartupFailure(Throwable cause) {
        logger.log(Level.SEVERE, "Failed to start application", cause);
    }

    /**
     * Called when a file is being loaded from the file system/
     *
     * @param fileName the name of the file being loaded.
     */
    public void loadingFromFilesystem(String fileName) {
        logger.info(String.format("Loading file %s from filesystem..", fileName));
    }

    /**
     * Called when the parsing of an excel file has started.
     */
    public void parsingStarted() {
        logger.info("Parsing excel file..");
    }

    /**
     * Called when the import of a file has started.
     *
     * @param indexName the index name of which importing has started for.
     */
    public void importStarted(String indexName) {
        logger.info(String.format("File parsed, starting import to %s..", indexName));
    }

    /**
     * Called when the import has completed successfully.
     */
    public void importCompleted() {
        logger.info("Import completed, shutting down.");
    }

    /**
     * Called when the bulk import to elasticsearch has failed.
     *
     * @param cause the cause of the failure.
     */
    public void onImportFailed(Throwable cause) {
        logger.log(Level.SEVERE, "Failed to import", cause);
    }

    /**
     * Called when parsing of a file has failed.
     *
     * @param fileName the name of the file that failed to parse.
     * @param e        the exception that caused the file to fail.
     */
    public void onParseFailed(String fileName, ParserException e) {
        logger.log(Level.SEVERE, String.format("Failed to import file %s", fileName), e);
    }

    /**
     * Called when the loading of a file has failed.
     *
     * @param fileName the name of the file that failed.
     * @param cause    the exception that caused the file to fail.
     */
    public void onFileLoadFailed(String fileName, Throwable cause) {
        logger.log(Level.SEVERE, String.format("Failed to load file %s", fileName), cause);
    }

    /**
     * called when the supplied command line is considered incomplete.
     *
     * @param args the args that was provided.
     */
    public void onCommandLineMissingArguments(String[] args) {
        logger.severe("Missing command line arguments, both <fileName> and <indexName> is required.");
        logger.severe("Found arguments: " + String.join(", ", args));
        logger.severe("Provided " + args.length + " arguments, at least two are required.");
    }

    /**
     * Emitted by the fileparser when it is parsing a file.
     *
     * @param fileName the name of the file being parsed.
     * @param offset   this is the row number of the excel file where the column titles are located.
     */
    public void parsingFile(String fileName, int offset) {
        logger.info(String.format("Parsing file '%s' using titles from row %d..", fileName, offset));
    }

    /**
     * Called when a file has been parsed by the {@link FileParser}.
     *
     * @param rows     the number of rows that was parsed from the file.
     * @param fileName the filename of the file that was parsed.
     */
    public void parsedFile(int rows, String fileName) {
        logger.info(String.format("Parsed %d rows from file %s.", rows, fileName));
    }

    /**
     * Called when a batch import has completed.
     *
     * @param response the http server response from the elasticsearch server.
     * @param total    the total number of items to import.
     * @param event    the import event that the batch belongs to.
     * @param received  the starting range of the imported items/
     * @param percent  the total progress of the import.
     */
    public void onImportedBatch(HttpClientResponse response, ImportEvent event, int total,
                                int received, float percent) {
        logger.info(
                String.format("Submitted items [%d -> %d] of %d with result [%d] %s into '%s' [%.1f%%]",
                        ((received > MAX_BATCH) ? (received - MAX_BATCH) - 1 : 0),
                        received - 1,
                        total,
                        response.statusCode(),
                        response.statusMessage(),
                        event.getIndex(),
                        percent)
        );
    }

    /**
     * Called when the elasticsearch writer is started.
     */
    public void onWriterStarted() {
        logger.info("Started elastic writer. tls = " + Configuration.isElasticTLS());
    }

    /**
     * @param version the version of the elasticsearch server that the writer connected to.
     */
    public void onWriterConnected(String version) {
        logger.info(String.format("Connected to elasticsearch server %s at %s:%d",
                version, Configuration.getElasticHost(), Configuration.getElasticPort()));
    }

    /**
     * converts a throwables stack trace into a string.
     *
     * @param throwable the throwable to be converted.
     * @return a textual representation of the throwables trace,
     * may be used in the app to display errors.
     */
    public static String traceToText(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    public void websiteStarted(int webPort) {
        logger.info("Started website on port " + webPort);
    }
}
