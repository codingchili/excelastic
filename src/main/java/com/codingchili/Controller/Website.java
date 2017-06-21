package com.codingchili.Controller;

import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.codingchili.ApplicationLauncher;
import com.codingchili.Model.Configuration;
import com.codingchili.Model.ElasticWriter;
import com.codingchili.Model.FileParser;
import com.codingchili.Model.ParserException;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.templ.JadeTemplateEngine;

import static com.codingchili.Model.FileParser.INDEX;

/**
 * @author Robin Duda
 *         <p>
 *         Manages the web interface and handles file uploads.
 */
public class Website extends AbstractVerticle {
    private Logger logger = Logger.getLogger(getClass().getName());
    private static final String DONE = "/done";
    private static final String ERROR = "/error";
    private static final String MESSAGE = "message";
    private static final String OFFSET = "offset";
    private static final String FILE = "file";
    private static final String IMPORTED = "imported";
    private static final String NO_FILE_WAS_UPLOADED = "No file was uploaded.";
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
    }

    @Override
    public void start(Future<Void> start) {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        setRouterAPI(router);
        router.route("/favicon.ico").handler(ctx -> ctx.response().end());
        router.route("/static/*").handler(StaticHandler.create());

        router.route("/*").handler(context -> {
            context.put("version", ApplicationLauncher.version);
            context.put("esVersion", ElasticWriter.getElasticVersion());
            context.put("esURL", Configuration.getElasticURL());
            context.next();
        });

        router.route("/*").handler(TemplateHandler.create(JadeTemplateEngine.create()));

        vertx.createHttpServer().requestHandler(router::accept).listen(Configuration.getWebPort(), done -> {
            if (done.succeeded()) {
                Configuration.setWebPort(done.result().actualPort());
                logger.info("Started website on port " + Configuration.getWebPort());
                start.complete();
            } else {
                start.fail(done.cause());
            }
        });
    }

    private void setRouterAPI(Router router) {
        router.route("/api/upload").handler(context -> {
            Iterator<FileUpload> iterator = context.fileUploads().iterator();

            if (iterator.hasNext()) {
                FileUpload upload = context.fileUploads().iterator().next();

                vertx.fileSystem().readFile(upload.uploadedFileName(), file -> {
                    parse(file.result(), context.request().params(), upload.fileName(),
                            Future.<Integer>future().setHandler(result -> {
                                if (result.succeeded()) {
                                    logger.info(String.format("Imported file %s successfully.", upload.fileName()));
                                    context.put(FILE, upload.fileName());
                                    context.put(IMPORTED, result.result());
                                    context.reroute(DONE);
                                } else {
                                    context.put(MESSAGE, traceToText(result.cause()));
                                    logger.log(Level.SEVERE, String.format("Failed to parse file %s.",
                                            upload.fileName()), result.cause());
                                    context.reroute(ERROR);
                                }
                            }));
                });
            } else {
                context.put(MESSAGE, NO_FILE_WAS_UPLOADED);
                context.reroute(ERROR);
            }
        });
    }

    private String traceToText(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private void parse(Buffer buffer, MultiMap params, String fileName, Future<Integer> future) {
        vertx.<Integer>executeBlocking(blocking -> {
            try {
                int columnRow = Integer.parseInt(params.get(OFFSET));
                FileParser parser = new FileParser(buffer.getBytes(), columnRow, fileName);
                vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH,
                        parser.toImportable(params.get(INDEX)), reply -> {
                    if (reply.succeeded()) {
                        blocking.complete(parser.getImportedItems());
                    } else {
                        blocking.fail(reply.cause());
                    }
                });
            } catch (ParserException | NumberFormatException e) {
                blocking.fail(new ParserException(e));
            }
        }, false, done -> {
            if (done.succeeded()) {
                future.complete(done.result());
            } else {
                future.fail(done.cause());
            }
        });
    }
}
