package com.codingchili;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.codingchili.Controller.Website;
import com.codingchili.Model.Configuration;
import com.codingchili.Model.ElasticWriter;
import com.codingchili.Model.FileParser;
import com.codingchili.Model.ParserException;

import io.vertx.core.*;

/**
 * @author Robin Duda
 *
 * Launcher class to bootstrap the application.
 */
public class Launcher {
    private static final Logger logger = Logger.getLogger(Launcher.class.getName());
    private static final String COMMAND_IMPORT = "import";
    private Vertx vertx = Vertx.vertx();

    public static void main(String[] args) {
        new Launcher(args);
    }

    public Launcher(String[] args) {
        logger.info("Starting application..");
        Future<Void> future = Future.future();

        future.setHandler(done -> {
            if (done.succeeded()) {
                logger.info("Successfully started application");
                getImportFileName(args).ifPresent(this::importFile);
            } else {
                logger.log(Level.SEVERE, "Failed to start application", done.cause());
            }
        });

        start(future);
    }

    private void importFile(String fileName) {
        logger.info(String.format("Loading file %s from filesystem..", fileName));
        vertx.fileSystem().readFile(fileName, file -> {
            if (file.succeeded()) {
                logger.info("Parsing XLSX file to JSON..");
                try {
                    FileParser parser = new FileParser(file.result().getBytes(), 1);
                    logger.info("File parsed, starting import to elasticsearch..");
                    vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH, parser.toImportableObject());
                } catch (ParserException e) {
                    logger.log(Level.SEVERE, String.format("Failed to import file %s", fileName));
                }
            } else {
                logger.log(Level.SEVERE, String.format("Failed to load file %s", fileName), file.cause());
            }
        });
    }

    private static Optional<String> getImportFileName(String[] args) {
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case COMMAND_IMPORT:
                    return Optional.of(args[++i]);
            }
        }
        return Optional.empty();
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
