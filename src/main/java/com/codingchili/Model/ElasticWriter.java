package com.codingchili.Model;

import com.codingchili.logging.ApplicationLogger;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.codingchili.Controller.Website.ACTION;
import static com.codingchili.Controller.Website.UPLOAD_ID;

/**
 * @author Robin Duda
 * <p>
 * Writes json data to elasticsearch for indexing.
 */
public class ElasticWriter extends AbstractVerticle {
    public static final String IMPORT_PROGRESS = "import.progress";
    public static final String ES_STATUS = "es-status";
    public static final int INDEXING_TIMEOUT = 3000000;
    public static final int MAX_BATCH = 128;

    private static final String BULK = "/_bulk";
    private static final int POLL = 5000;
    private static final String PROGRESS = "progress";
    public static final String IMPORT = "import";
    private static boolean connected = false;
    private static String version = "";

    private ApplicationLogger logger = new ApplicationLogger(getClass());
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
        vertx.setPeriodic(POLL, this::pollElasticServer);
    }

    @Override
    public void start(Future<Void> start) {
        startSubmitListener();
        logger.onWriterStarted();
        start.complete();
        pollElasticServer(0L);
    }

    /**
     * Listens on the event bus for files.
     */
    private void startSubmitListener() {
        vertx.eventBus().consumer(Configuration.INDEXING_ELASTICSEARCH, handler -> {
            ImportEvent event = (ImportEvent) handler.body();

            clearBeforeIndexing(done -> {

                event.getParser().subscribe(new Subscriber<JsonObject>() {
                    private AtomicInteger parsed = new AtomicInteger(0);
                    private AtomicInteger sent = new AtomicInteger(0);
                    private AtomicBoolean complete = new AtomicBoolean(false);
                    private HttpClientRequest request = openChunkedRequest();
                    private String header = createImportHeader(event);
                    private Subscription subscription;

                    private HttpClientRequest openChunkedRequest() {
                        return post(event.getIndex() + BULK)
                                .handler(response -> response.bodyHandler(body -> {

                                    // update the progress on finished batch submission.
                                    updateStatus(response, event, sent.get());

                                    if (complete.get()) {
                                        // signal completion over the cluster.
                                        handler.reply(null);
                                    } else {
                                        // request more items - we dont do this until the current request
                                        // is finished with a status code.
                                        subscription.request(MAX_BATCH);
                                    }
                                })).exceptionHandler(this::onError).setChunked(true);
                    }

                    private void endChunkedRequest() {
                        // ends the request forcing the remote to process all submitted items and
                        // provide a status code before we continue.
                        request.end();
                    }

                    @Override
                    public void onSubscribe(Subscription subscription) {
                        this.subscription = subscription;
                        // we use -1 here to guarantee that there is only one simultaneous request in flight.
                        // when the in-flight request is commited we only need to parse 1 element before we
                        // can end the next chunked request.
                        subscription.request(MAX_BATCH * 2 - 1);
                    }

                    @Override
                    public void onNext(JsonObject entry) {
                        vertx.runOnContext(on -> {
                            writeToChunkedRequest(request, header, entry);

                            int done = parsed.incrementAndGet();
                            int total = event.getParser().getNumberOfElements();

                            if (done % MAX_BATCH == 0 || done >= total) {
                                sent.set(done);
                                endChunkedRequest();

                                if (done != total) {
                                    request = openChunkedRequest();
                                } else {
                                    complete.set(true);
                                    subscription.cancel();
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        logger.onError(throwable);
                        handler.fail(500, ApplicationLogger.traceToText(throwable));
                        subscription.cancel();
                    }

                    @Override
                    public void onComplete() {
                        // do nothing until all items are indexed.
                    }
                });
            }, event);
        });
    }

    /**
     * Emits a status event to the console and any listening remote clients.
     *
     * @param response the http response returned from ending the last chunked write.
     * @param event    the import event that is being processed.
     * @param received the number of elements that has been indexed.
     */
    private void updateStatus(HttpClientResponse response, ImportEvent event, int received) {

        float percent = (received * 1.0f / event.getParser().getNumberOfElements()) * 100;
        logger.onImportedBatch(response, event, event.getParser().getNumberOfElements(), received, percent);

        vertx.eventBus().publish(IMPORT_PROGRESS, new JsonObject()
                .put(ACTION, IMPORT)
                .put(PROGRESS, percent)
                .put(UPLOAD_ID, event.getUploadId()));
    }

    private String createImportHeader(ImportEvent event) {
        return new JsonObject()
                .put("index", new JsonObject()
                        .put("_index", event.getIndex())
                        .put("_type", event.getMapping())).encode() + "\n";
    }

    /**
     * Builds a bulk query for insertion into elasticsearch
     *
     * @param request the request to stream the bulk insert into.
     * @param header  the static header that indicates which index to import the item into.
     * @param json    the current item to import into the index.
     */
    private void writeToChunkedRequest(HttpClientRequest request, String header, JsonObject json) {
        request.write(header);
        request.write(json.toBuffer());
        request.write("\n");
    }

    private void clearBeforeIndexing(Handler<AsyncResult<?>> done, ImportEvent event) {
        if (event.getClearExisting()) {
            delete("/" + event.getIndex()).handler(req -> {
                done.handle(Future.succeededFuture());
            }).end();
        } else {
            done.handle(Future.succeededFuture());
        }
    }

    private HttpClientRequest post(String path) {
        HttpClientRequest client = vertx.createHttpClient().post(getOptions(path));
        addHeaders(client);
        return client;
    }

    private void addHeaders(HttpClientRequest client) {

        // comply with ElasticSearch 6.0 - strict content type.
        client.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON);

        // support basic authentication.
        Configuration.getBasicAuth().ifPresent(auth -> {
            client.putHeader(HttpHeaderNames.AUTHORIZATION, "Basic " + auth);
        });
    }

    private RequestOptions getOptions(String path) {
        return new RequestOptions()
                .setPort(Configuration.getElasticPort())
                .setHost(Configuration.getElasticHost())
                .setSsl(Configuration.isElasticTLS())
                .setURI(path);
    }

    /**
     * Polls the elasticsearch server for version information. Sets connected if the server
     * is available.
     *
     * @param id the id of the timer that triggered the request, not used.
     */
    private void pollElasticServer(Long id) {
        get("/").handler(handler -> handler.bodyHandler((buffer -> {
            version = buffer.toJsonObject().getJsonObject("version").getString("number");
            if (!connected) {
                logger.onWriterConnected(version);
                connected = true;
                vertx.eventBus().send(ES_STATUS, connected);
            }
        })).exceptionHandler(error -> {
            connected = false;
            logger.onError(error);
            vertx.eventBus().send(ES_STATUS, connected);
        })).end();
    }

    private HttpClientRequest get(String path) {
        HttpClientRequest request = vertx.createHttpClient().get(getOptions(path));
        addHeaders(request);
        return request;
    }

    private HttpClientRequest delete(String path) {
        HttpClientRequest request = vertx.createHttpClient().delete(getOptions(path));
        addHeaders(request);
        return request;
    }

    public static String getElasticVersion() {
        return version;
    }

    public static boolean isConnected() {
        return connected;
    }
}
