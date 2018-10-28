package com.codingchili.Controller;

import com.codingchili.Model.*;
import com.codingchili.logging.ApplicationLogger;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;

import java.io.*;

import static com.codingchili.Model.ElasticWriter.INDEXING_TIMEOUT;

/**
 * @author Robin Duda
 * <p>
 * Implements the commandline import functionality.
 */
public class CommandLine {
    private ApplicationLogger logger = new ApplicationLogger(CommandLine.class);
    private Vertx vertx;

    public CommandLine(Vertx vertx, String[] args) {
        this.vertx = vertx;
        assertCommandLineValid(args);
        importFile(ImportEvent.fromCommandLineArgs(args), args[0]);
    }

    /**
     * Makes sure that the given commandline options contains all the required values.
     *
     * @param args the commandline arguments.
     */
    private void assertCommandLineValid(String[] args) {
        if (args.length < 2) {
            vertx.close(closed -> logger.onCommandLineMissingArguments(args));
        }
    }


    /**
     * Imports a file from the commandline.
     *
     * @param event    the import event - contains information like index and mapping to use.
     * @param fileName the name of the file to be imported.
     */
    private void importFile(ImportEvent event, String fileName) {
        logger.loadingFromFilesystem(fileName);
        logger.parsingStarted();
        FileParser parser = ParserFactory.getByFilename(fileName);
        try {
            parser.setFileData(fileName, 1, fileName);

            event.setParser(parser);
            parser.initialize();

            logger.importStarted(event.getIndex());
            vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH, event, getDeliveryOpts(),
                    done -> {
                        if (done.succeeded()) {
                            logger.importCompleted();
                        } else {
                            logger.onImportFailed(done.cause());
                        }
                        System.exit(0); // vertx.close gives an error: "result already completed: success"
                    });
        } catch (ParserException e) {
            logger.onParseFailed(fileName, e);
        } catch (FileNotFoundException e) {
            logger.onFileLoadFailed(fileName, e);
        } finally {
            parser.free();
        }
    }

    private DeliveryOptions getDeliveryOpts() {
        return new DeliveryOptions().setSendTimeout(INDEXING_TIMEOUT);
    }

}
