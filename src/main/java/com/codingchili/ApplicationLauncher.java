package com.codingchili;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.codingchili.Controller.Website;
import com.codingchili.Model.Configuration;
import com.codingchili.Model.ElasticWriter;
import com.codingchili.Model.FileParser;
import com.codingchili.Model.ParserException;

import io.vertx.core.*;

import static com.codingchili.Model.ElasticWriter.ES_STATUS;

/**
 * @author Robin Duda
 *
 * ApplicationLauncher class to bootstrap the application.
 */
public class ApplicationLauncher {
    private static final Logger logger = Logger.getLogger(ApplicationLauncher.class.getName());
    public static String version = "1.2.0";
    private Vertx vertx = Vertx.vertx();
    private String[] args;

    public static void main(String[] args) {
        new ApplicationLauncher(args);
    }

    public ApplicationLauncher(String[] args) {
        this.args = args;

        logger.info(String.format("Starting excelastic %s..", version));
        Future<Void> future = Future.future();

        future.setHandler(done -> {
            if (done.succeeded()) {
                logger.info("Successfully started application");

                if (args.length == 3) {
                    importFile(getFileName(), getIndexName());
                } else {
                    vertx.eventBus().consumer(ES_STATUS, message -> {
                        logger.info("Attempting to open browser..");
                        try {
                            Desktop.getDesktop().browse(new URI(Configuration.getWebsiteURL()));
                        } catch (IOException | URISyntaxException e) {
                            logger.warning(e.getMessage());
                        }
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
        return args[1];
    }

    private String getIndexName() {
        return args[2];
    }

    private void importFile(String fileName, String indexName) {
        logger.info(String.format("Loading file %s from filesystem..", fileName));
        vertx.fileSystem().readFile(fileName, file -> {
            if (file.succeeded()) {
                logger.info("Parsing XLSX file to JSON..");
                try {
                    FileParser parser = new FileParser(file.result().getBytes(), 1, fileName);
                    logger.info("File parsed, starting import to elasticsearch..");
                    vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH, parser.toImportable(indexName));
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
