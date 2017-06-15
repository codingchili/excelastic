package com.codingchili.Controller;

import com.codingchili.Model.*;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.templ.JadeTemplateEngine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * @author Robin Duda
 *
 * Manages the web interface and handles file uploads.
 */
public class Website extends AbstractVerticle {
    private Logger logger = Logger.getLogger(getClass().getName());
    private static final String DONE = "/done";
    private static final String ERROR = "/error";
    private static final String MESSAGE = "message";
    private static final String OFFSET = "offset";
    private static final String FILE = "file";
    private static final String NO_FILE_WAS_UPLOADED = "No file was uploaded.";
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
    }

    @Override
    public void start(Future<Void> start) {
        Router router = Router.router(vertx);
        JadeTemplateEngine engine = JadeTemplateEngine.create();

        router.route().handler(BodyHandler.create());

        setRouterAPI(router);
        router.route("/static/*").handler(StaticHandler.create());
        router.route("/*").handler(TemplateHandler.create(engine));

        vertx.createHttpServer().requestHandler(router::accept).listen(Configuration.WEB_PORT, done -> {
            if (done.succeeded()) {
                logger.info("Started website on port " + Configuration.WEB_PORT);
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
                    try {
                        parse(file.result(), context.request().params());
                        context.put(FILE, upload.fileName());
                        context.reroute(DONE);
                    } catch (ParserException e) {
                        context.put(MESSAGE, traceToText(e));
                        context.reroute(ERROR);
                    }
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

    private void parse(Buffer buffer, MultiMap params) throws ParserException {
        try {
            int columnRow = Integer.parseInt(params.get(OFFSET));
            FileParser parser = new FileParser(buffer.getBytes(), columnRow);
            sendOutput(parser.toImportableObject());
        } catch (NumberFormatException e) {
            throw new ParserException(e);
        }
    }

    private void sendOutput(JsonObject data) {
        vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH, data);
    }
}
