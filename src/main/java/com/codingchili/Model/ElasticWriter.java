package com.codingchili.Model;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.logging.Logger;

import static com.codingchili.Controller.Website.*;
import static com.codingchili.Model.FileParser.ITEMS;

/**
 * @author Robin Duda
 * <p>
 * Writes json data to elasticsearch for indexing.
 */
public class ElasticWriter extends AbstractVerticle {
    public static final int INDEXING_TIMEOUT = 3000000;
    private static final String INDEX = "index";
    private static final String BULK = "/_bulk";
    private static final int POLL = 5000;
    private static final int MAX_BATCH = 255;
    public static final String ES_STATUS = "es-status";
    private static final String PROGRESS = "progress";
    public static final String IMPORT_PROGRESS = "import.progress";
    private static boolean connected = false;
    private static String version = "";
    private Logger logger = Logger.getLogger(getClass().getName());
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
        vertx.setPeriodic(POLL, this::pollElasticServer);
    }

    @Override
    public void start(Future<Void> start) {
        startSubmitListener();
        logger.info("Started elastic writer");
        start.complete();
        pollElasticServer(0L);
    }

    /**
     * Listens on the event bus for files.
     */
    private void startSubmitListener() {
        vertx.eventBus().consumer(Configuration.INDEXING_ELASTICSEARCH, handler -> {
            JsonObject data = (JsonObject) handler.body();
            JsonArray items = data.getJsonArray(ITEMS);

            clearBeforeIndexing(done -> {
                Future<Void> starter = Future.future();
                Future<Void> next = starter;

                // performs one bulk request for each bucket of MAX_BATCH serially.
                for (int i = 0; i < items.size(); i += MAX_BATCH) {
                    final int current = i;
                    final int max = ((current + MAX_BATCH < items.size()) ? current + MAX_BATCH : items.size());
                    next = next.compose(v -> submitForIndexing(items, data, current, max));
                }

                // when the final submission completes complete the handler.
                next.setHandler((v) -> {
                    if (v.succeeded()) {
                        handler.reply(null);
                    } else {
                        handler.fail(500, v.cause().getMessage());
                    }
                });
                starter.complete();
            }, data);
        });
    }

    private void clearBeforeIndexing(Handler<AsyncResult<?>> done, JsonObject data) {
        if (data.getBoolean(CLEAR)) {
            delete("/" + data.getString(INDEX)).handler(req -> {
                done.handle(Future.succeededFuture());
            }).end();
        } else {
            done.handle(Future.succeededFuture());
        }
    }

    /**
     * Submits a subset of the given json array for indexing.
     *
     * @param items   items to be indexed
     * @param data    the data to import, contains index and template.
     * @param current the low bound for the given json items to import
     * @param max     the high bound for the given json items to import
     * @return a future completed when the indexing of the specified elements have completed.
     */
    private Future<Void> submitForIndexing(JsonArray items, JsonObject data, int current, int max) {
        Future<Void> future = Future.future();
        String index = data.getString(INDEX);
        String mapping = data.getString(MAPPING);

        post(index + BULK).handler(response -> response.bodyHandler(body -> {
            float percent = (max * 1.0f / items.size()) * 100;
            logger.info(
                    String.format("Submitted items [%d -> %d] of %d with result [%d] %s into '%s' [%.1f%%]",
                            current, max - 1, items.size(), response.statusCode(), response.statusMessage(), index, percent));

            vertx.eventBus().publish(IMPORT_PROGRESS, new JsonObject()
                    .put(PROGRESS, percent)
                    .put(UPLOAD_ID, data.getString(UPLOAD_ID)));

            future.complete();
        })).exceptionHandler(exception -> future.fail(exception.getMessage()))
                .end(bulkQuery(items, index, mapping, max, current));
        return future;
    }

    private HttpClientRequest post(String path) {
        HttpClientRequest client = vertx.createHttpClient()
                .post(Configuration.getElasticPort(), Configuration.getElasticHost(), path);

        addSecurityHeaders(client);
        return client;
    }

    private void addSecurityHeaders(HttpClientRequest request) {
        Configuration.getBasicAuth().ifPresent(auth -> {
            request.putHeader(HttpHeaderNames.AUTHORIZATION, "Basic " + auth);
        });
    }

    /**
     * Builds a bulk query for insertion into elasticsearch
     *
     * @param list    full list of all elements
     * @param index   the current item anchor
     * @param max     max upper bound of items to include in the bulk
     * @param current lower bound of items to include in the bulk
     * @return a payload encoded as json-lines.
     */
    private String bulkQuery(JsonArray list, String index, String mapping, int max, int current) {
        StringBuilder query = new StringBuilder();
        String header = new JsonObject()
                .put("index", new JsonObject()
                        .put("_index", index)
                        .put("_type", mapping)).encode() + "\n";

        for (int i = current; i < max; i++) {
            query.append(header).append(list.getJsonObject(i).encode()).append("\n");
        }

        return query.toString();
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
                logger.info(String.format("Connected to elasticsearch server %s at %s:%d",
                        version, Configuration.getElasticHost(), Configuration.getElasticPort()));
                connected = true;
                vertx.eventBus().send(ES_STATUS, connected);
            }
        })).exceptionHandler(event -> {
            connected = false;
            logger.severe(event.getMessage());
            vertx.eventBus().send(ES_STATUS, connected);
        })).end();
    }

    private HttpClientRequest get(String path) {
        HttpClientRequest request = vertx.createHttpClient().get(Configuration.getElasticPort(), Configuration.getElasticHost(), path);
        addSecurityHeaders(request);
        return request;
    }

    private HttpClientRequest delete(String path) {
        HttpClientRequest request = vertx.createHttpClient().delete(Configuration.getElasticPort(), Configuration.getElasticHost(), path);
        addSecurityHeaders(request);
        return request;
    }

    public static String getElasticVersion() {
        return version;
    }

    public static boolean isConnected() {
        return connected;
    }
}
