package com.codingchili;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.codingchili.Controller.Website;
import com.codingchili.Model.Configuration;
import com.codingchili.Model.ElasticWriter;
import com.codingchili.Model.FileParser;
import com.codingchili.Model.ParserException;

import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;

import static com.codingchili.Model.ElasticWriter.ES_STATUS;
import static com.codingchili.Model.ElasticWriter.INDEXING_TIMEOUT;

/**
 * @author Robin Duda
 *
 * Launcher class to bootstrap the application.
 */
public class ApplicationLauncher {
    private static final Logger logger = Logger.getLogger(ApplicationLauncher.class.getName());
    public static final String PARAM_CLEAR = "--clear";
    public static String VERSION = "1.2.5";
    private Vertx vertx = Vertx.vertx();
    private String[] args;

    public static void main(String[] args) {
        new ApplicationLauncher(args);
    }

    public ApplicationLauncher(String[] args) {
        this.args = args;

        logger.info(String.format("Starting excelastic %s..", VERSION));
        logger.info("to import files without the web interface use: <filename> <index> <mapping>");
        Future<Void> future = Future.future();

        future.setHandler(done -> {
            if (done.succeeded()) {
                logger.info("Successfully started application");

                if (args.length > 1) {
                    importFile(getFileName(), getIndexName());
                } else {
                    MessageConsumer<?> consumer = vertx.eventBus().consumer(ES_STATUS);
                    consumer.handler(message -> {
                        logger.info(String.format("Attempting to open browser.. [ES connected=%s]", message.body().toString()));
                        try {
                            Desktop.getDesktop().browse(new URI(Configuration.getWebsiteURL()));
                        } catch (IOException | URISyntaxException e) {
                            logger.warning(e.getMessage());
                        }
                        consumer.pause();
                    });
                }
            } else {
                logger.log(Level.SEVERE, "Failed to start application", done.cause());
                vertx.close();
            }
        });
        start(future);
    }

    private String getFileName() {
        return args[0];
    }

    private String getIndexName() {
        return args[1];
    }

    private Boolean clearExisting() {
        return Arrays.stream(args).anyMatch(param -> param.equals(PARAM_CLEAR));
    }

    private String getMapping() {
        return (args.length > 2) ? args[2] : "default";
    }

    /**
     * Imports a file from the commandline.
     * @param fileName the name of the file to import.
     * @param indexName the name of the index to import the file into.
     */
    private void importFile(String fileName, String indexName) {
        logger.info(String.format("Loading file %s from filesystem..", fileName));
        vertx.fileSystem().readFile(fileName, file -> {
            if (file.succeeded()) {
                logger.info("Parsing XLSX file to JSON..");
                try {
                    FileParser parser = new FileParser(file.result().getBytes(), 1, fileName);
                    logger.info(String.format("File parsed, starting import to %s..", indexName));
                    vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH,
                            parser.toImportable(indexName, getMapping(), clearExisting()),
                            new DeliveryOptions().setSendTimeout(INDEXING_TIMEOUT),
                            done -> {
                        if (done.succeeded()) {
                            logger.info("Import completed, shutting down.");
                        } else {
                            logger.log(Level.SEVERE, "Failed to import", done.cause());
                        }
                        System.exit(0); // vertx.close gives an error: "result already completed: success"
                    });
                } catch (ParserException e) {
                    logger.log(Level.SEVERE, String.format("Failed to import file %s", fileName));
                }
            } else {
                logger.log(Level.SEVERE, String.format("Failed to load file %s", fileName), file.cause());
            }
        });
    }

    public void start(Future<Void> start) {
        Future<String> writer = Future.future();
        Future<String> website = Future.future();
        vertx.deployVerticle(new ElasticWriter(), writer.completer());
        vertx.deployVerticle(new Website(), website.completer());

        CompositeFuture.all(writer, website).setHandler(done -> {
            if (done.succeeded()) {
                start.complete();
            } else {
                start.fail(done.cause());
            }
        });
    }
}
