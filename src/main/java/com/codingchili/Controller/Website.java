package com.codingchili.Controller;

import com.codingchili.Model.*;
import com.codingchili.logging.ApplicationLogger;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.*;
import io.vertx.ext.web.handler.*;
import io.vertx.ext.web.templ.JadeTemplateEngine;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.codingchili.ApplicationLauncher.VERSION;
import static com.codingchili.Model.Configuration.INDEXING_ELASTICSEARCH;
import static com.codingchili.Model.ElasticWriter.*;
import static com.codingchili.Model.ExcelParser.INDEX;

/**
 * @author Robin Duda
 * <p>
 * Manages the web interface and handles file uploads.
 */
public class Website extends AbstractVerticle {
    public static final String UPLOAD_ID = "uploadId";
    public static final String ACTION = "action";
    private static final String DONE = "/done";
    private static final String ERROR = "/error";
    private static final String MESSAGE = "message";
    private static final String FILE = "file";
    private static final String IMPORTED = "imported";
    private static final String NO_FILE_WAS_UPLOADED = "No file was uploaded.";
    private static final String VERIFY = "verify";
    private Logger logger = Logger.getLogger(getClass().getName());
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
            context.put("index", Configuration.getDefaultIndex());
            context.put("supportedFiles", String.join(", ", ParserFactory.getSupportedExtensions()));
            context.next();
        });

        router.route("/*").handler(TemplateHandler.create(JadeTemplateEngine.create()));
        startWebsite(start, router);
    }

    private void startWebsite(Future<Void> start, Router router) {
        setupStatusService().requestHandler(router::accept).listen(Configuration.getWebPort(), done -> {
            if (done.succeeded()) {
                Configuration.setWebPort(done.result().actualPort());
                logger.info("Started website on port " + Configuration.getWebPort());
                start.complete();
            } else {
                start.fail(done.cause());
            }
        });
    }

    private HttpServer setupStatusService() {
        return vertx.createHttpServer().websocketHandler(websock -> {
            websock.writeFinalTextFrame(new JsonObject().put("message", getStatusServiceWelcomeMessage()).encode());

            AtomicReference<String> uploadId = new AtomicReference<>("");

            // sets up an event bus consumer to listen for import progress.
            MessageConsumer<JsonObject> consumer = vertx.eventBus().consumer(IMPORT_PROGRESS, data -> {
                try {
                    if (uploadId.get().equals(data.body().getString(UPLOAD_ID))) {
                        websock.writeFinalTextFrame(data.body().encode());
                    }
                } catch (Throwable e) {
                    websock.close();
                }
            });
            // we only support one message from the client - to set the upload ID to listen to.
            websock.handler(handler -> uploadId.set(handler.toJsonObject().getString(UPLOAD_ID)));

            // when the websocket is closed we should stop listening for status messages.
            websock.closeHandler(closed -> consumer.unregister());

            // when the websocket excepts we should also stop listening for status messages.
            websock.exceptionHandler(sock -> consumer.unregister());
        });
    }

    private String getStatusServiceWelcomeMessage() {
        return "websocket connected to excelastic " + VERSION + " using ElasticSearch " + getElasticVersion();
    }

    /**
     * Adds the upload route to the given router
     *
     * @param router the upload route is added to the given router
     */
    private void setRouterAPI(Router router) {
        // API route for handling file uploads.
        router.route("/api/upload").handler(context -> {
            Iterator<FileUpload> iterator = context.fileUploads().iterator();

            if (iterator.hasNext()) {
                MultiMap params = context.request().params();
                logger.info("Receiving uploaded file with request id " + params.get(UPLOAD_ID));
                FileUpload upload = context.fileUploads().iterator().next();

                parse(upload.uploadedFileName(), params, upload.fileName(), onComplete(context, upload.fileName()));
            } else {
                context.put(MESSAGE, NO_FILE_WAS_UPLOADED);
                context.reroute(ERROR);
            }
        });
    }

    /**
     * Creates a future that is called when the import completes either successfully or by an error.
     *
     * @param context  the routing context the upload was initiated from.
     * @param fileName the file name of the file that was uplaoded.
     * @return a future to be completed when {@link #parse(String, MultiMap, String, Future)}  completes}.
     */
    private Future<Integer> onComplete(RoutingContext context, String fileName) {
        // when the file has been read from disk, parsed and imported.
        return Future.<Integer>future().setHandler(result -> {
            if (result.succeeded()) {
                String index = context.request().params().get(INDEX);
                logger.info(String.format("Imported file '%s' successfully into '%s'.", fileName, index));

                context.put(INDEX, index);
                context.put(FILE, fileName);
                context.put(IMPORTED, result.result());
                context.reroute(DONE);
            } else {
                // oops: the import has failed, make sure to emit the full error to clients.
                context.put(MESSAGE, ApplicationLogger.traceToText(result.cause()));
                logger.log(Level.SEVERE, String.format("Failed to parse file '%s'.", fileName), result.cause());
                context.reroute(ERROR);
            }
        });
    }

    /**
     * Parses a file upload request, converting the excel payload into json and waits
     * for elasticsearch to complete indexing.
     *
     * @param uploadedFileName the actual file on disk that contains the uploaded file.
     * @param params   upload parameters
     * @param fileName the name of the uploaded file
     * @param future   callback on completed parse + indexing.
     */
    private void parse(String uploadedFileName, MultiMap params, String fileName, Future<Integer> future) {
        vertx.executeBlocking(blocking -> {
            FileParser parser = ParserFactory.getByFilename(fileName);
            try {
                ImportEvent event = ImportEvent.fromParams(params);
                parser.setFileData(uploadedFileName, event.getOffset(), fileName);

                sendParsingEvent(event);
                parser.initialize();
                event.setParser(parser);

                // submit an import event.
                vertx.eventBus().send(INDEXING_ELASTICSEARCH, event, getDeliveryOptions(),
                        reply -> {
                            if (reply.succeeded()) {
                                blocking.complete(parser.getNumberOfElements());
                            } else {
                                blocking.fail(reply.cause());
                            }
                        });
            } catch (FileNotFoundException | ParserException | NumberFormatException e) {
                blocking.fail(e);
            } finally {
                parser.free();
            }
        }, false, future);
    }

    private void sendParsingEvent(ImportEvent event) {
        vertx.eventBus().publish(IMPORT_PROGRESS, new JsonObject()
                .put(ACTION, VERIFY)
                .put(UPLOAD_ID, event.getUploadId()));
    }

    private DeliveryOptions getDeliveryOptions() {
        return new DeliveryOptions().setSendTimeout(INDEXING_TIMEOUT);
    }
}