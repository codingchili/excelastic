package com.codingchili;

import com.codingchili.Controller.CommandLine;
import com.codingchili.Controller.Website;
import com.codingchili.Model.*;
import com.codingchili.logging.ApplicationLogger;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import static com.codingchili.Model.ElasticWriter.*;

/**
 * @author Robin Duda
 *
 * Launcher class to bootstrap the application.
 */
public class ApplicationLauncher {
    private final ApplicationLogger logger = new ApplicationLogger(getClass());
    public static String VERSION = "1.3.3";
    private Vertx vertx;

    public static void main(String[] args) {
        new ApplicationLauncher(args);
    }

    private ApplicationLauncher(String[] args) {
        VertxOptions options = new VertxOptions();
        options.setMaxEventLoopExecuteTime(options.getMaxEventLoopExecuteTime() * 10)
                .setBlockedThreadCheckInterval(8000);
        vertx = Vertx.vertx();

        ImportEventCodec.registerOn(vertx);

        logger.startupMessage();

        start().setHandler(done -> {
            if (done.succeeded()) {
                logger.applicationStartup();

                if (args.length > 1) {
                    // import file from the command line.
                    new CommandLine(vertx, args);
                } else {
                    // wait for the elasticsearch server to come online to show the import UI.
                    waitForElasticServerAvailability();
                }
            } else {
                logger.applicationStartupFailure(done.cause());
                vertx.close();
            }
        });
    }

    /**
     * Waits until there is a connection to the ElasticSearch server and then
     * attempts to open the browser if one is available to the import website.
     */
    private void waitForElasticServerAvailability() {
        MessageConsumer<?> consumer = vertx.eventBus().consumer(ES_STATUS);
        consumer.handler(message -> {
            logger.openingBrowser(message.body().toString());
            try {
                Desktop.getDesktop().browse(new URI(Configuration.getWebsiteURL()));
            } catch (IOException | URISyntaxException e) {
                logger.onError(e);
            }
            consumer.pause();
        });
    }

    /**
     * Deploys the elastic writer and the website.
     * @return application deployment callback.
     */
    public CompositeFuture start() {
        Future<String> writer = Future.future();
        Future<String> website = Future.future();
        vertx.deployVerticle(new ElasticWriter(), writer.completer());
        vertx.deployVerticle(new Website(), website.completer());
        return CompositeFuture.all(writer, website);
    }
}
