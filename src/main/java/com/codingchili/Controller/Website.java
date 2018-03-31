package com.codingchili.Controller;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.codingchili.Model.Configuration;
import com.codingchili.Model.ElasticWriter;
import com.codingchili.Model.FileParser;
import com.codingchili.Model.ParserException;
import static com.codingchili.ApplicationLauncher.VERSION;
import static com.codingchili.Model.Configuration.INDEXING_ELASTICSEARCH;
import static com.codingchili.Model.ElasticWriter.*;
import static com.codingchili.Model.FileParser.INDEX;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.TemplateHandler;
import io.vertx.ext.web.templ.JadeTemplateEngine;

/**
 * @author Robin Duda
 * <p>
 * Manages the web interface and handles file uploads.
 */
public class Website extends AbstractVerticle {
    public static final String MAPPING = "mapping";
    public static final String UPLOAD_ID = "uploadId";
    public static final String OPTIONS = "options";
    public static final String CLEAR = "clear";
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

        // adds values used in the template to all routes.
        router.route("/*").handler(context -> {
            context.put("version", VERSION);
            context.put("esVersion", ElasticWriter.getElasticVersion());
            context.put("esURL", Configuration.getElasticURL());
            context.put("connected", ElasticWriter.isConnected());
            context.put("tls", Configuration.isElasticTLS());
            context.next();
        });

        router.route("/*").handler(TemplateHandler.create(JadeTemplateEngine.create()));

        vertx.createHttpServer().websocketHandler(websock -> {
            websock.writeFinalTextFrame(new JsonObject().put("message", "websocket connected to excelastic " + VERSION +
                " using ElasticSearch " + getElasticVersion()).encode());

            Atomic<String> uploadId = new Atomic<>("");
            MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(IMPORT_PROGRESS, data -> {
                try {
                    if (uploadId.get().equals(data.body().getString(UPLOAD_ID))) {
                        websock.writeFinalTextFrame(data.body().encode());
                    }
                } catch (Throwable e) {
                    websock.close();
                }
            });
            websock.handler(handler -> uploadId.set(handler.toJsonObject().getString(UPLOAD_ID)));
            websock.closeHandler(closed -> consumer.unregister());
            websock.exceptionHandler(sock -> consumer.unregister());

        }).requestHandler(router::accept).listen(Configuration.getWebPort(), done -> {
            if (done.succeeded()) {
                Configuration.setWebPort(done.result().actualPort());
                logger.info("Started website on port " + Configuration.getWebPort());
                start.complete();
            } else {
                start.fail(done.cause());
            }
        });
    }

    /**
     * Adds the upload route to the given router
     *
     * @param router the upload route is added to the given router
     */
    private void setRouterAPI(Router router) {
        router.route("/api/upload").handler(context -> {
            Iterator<FileUpload> iterator = context.fileUploads().iterator();

            if (iterator.hasNext()) {
                MultiMap params = context.request().params();
                logger.info("Receiving uploaded file with request id " + params.get(UPLOAD_ID));
                FileUpload upload = context.fileUploads().iterator().next();

                vertx.fileSystem().readFile(upload.uploadedFileName(), file -> {
                    parse(file.result(), params, upload.fileName(),
                        Future.<Integer>future().setHandler(result -> {
                            if (result.succeeded()) {
                                String index = context.request().params().get(INDEX);
                                logger.info(String.format("Imported file '%s' successfully into '%s'.",
                                    upload.fileName(), index));

                                context.put(INDEX, index);
                                context.put(FILE, upload.fileName());
                                context.put(IMPORTED, result.result());
                                context.reroute(DONE);
                            } else {
                                context.put(MESSAGE, traceToText(result.cause()));
                                logger.log(Level.SEVERE, String.format("Failed to parse file '%s'.",
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

    /**
     * converts a throwables stack trace into a string.
     *
     * @param throwable the throwable to be converted.
     * @return a textual representation of the throwables trace,
     * may be used in the app to display errors.
     */
    private String traceToText(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    /**
     * Parses a file upload request, converting the excel payload into json and waits
     * for elasticsearch to complete indexing.
     *
     * @param buffer   contains the excel file data
     * @param params   upload parameters
     * @param fileName the name of the uploaded file
     * @param future   callback on completed parse + indexing.
     */
    private void parse(Buffer buffer, MultiMap params, String fileName, Future<Integer> future) {
        vertx.<Integer>executeBlocking(blocking -> {
            try {
                int columnRow = Integer.parseInt(params.get(OFFSET));
                FileParser parser = new FileParser(buffer.getBytes(), columnRow, fileName);
                JsonObject data = parser.toImportable(
                    params.get(INDEX),
                    getMappingByParams(params),
                    params.get(OPTIONS).equals(CLEAR));

                data.put(UPLOAD_ID, params.get(UPLOAD_ID));

                vertx.eventBus().send(INDEXING_ELASTICSEARCH, data, new DeliveryOptions().setSendTimeout(INDEXING_TIMEOUT),
                    reply -> {
                        if (reply.succeeded()) {
                            blocking.complete(parser.getImportedItems());
                        } else {
                            blocking.fail(reply.cause());
                        }
                    });
            } catch (ParserException | NumberFormatException e) {
                blocking.fail(e);
            }
        }, false, done -> {
            if (done.succeeded()) {
                future.complete(done.result());
            } else {
                future.fail(done.cause());
            }
        });
    }

    private String getMappingByParams(MultiMap params) {
        return (params.get(MAPPING).length() == 0) ? "default" : params.get(MAPPING);
    }
}
