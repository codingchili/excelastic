package com.codingchili.Model;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import static com.codingchili.Model.FileParser.ITEMS;

/**
 * @author Robin Duda
 *         <p>
 *         Writes json data to elasticsearch for indexing.
 */
public class ElasticWriter extends AbstractVerticle {
    private static final String INDEX = "index";
    private static final String BULK = "/_bulk";
    private static final int POLL = 5000;
    private static final int MAX_BATCH = 255;
    public static final String ES_STATUS = "es-status";
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
            String index = data.getString(INDEX);
            CountDownLatch latch = new CountDownLatch(getBatchCount(items.size()));

            // performs one bulk request for each bucket of MAX_BATCH
            for (int i = 0; i < items.size(); i += MAX_BATCH) {
                final int max = ((i + MAX_BATCH < items.size()) ? i + MAX_BATCH : items.size());
                final int current = i;
                vertx.createHttpClient().post(
                        Configuration.getElasticPort(), Configuration.getElasticHost(), index + BULK)
                        .handler(response -> {
                            logger.info(String.format("Submitted items [%d -> %d] with result [%d] %s",
                                    current, max - 1, response.statusCode(), response.statusMessage()));

                            response.bodyHandler(body -> {
                                latch.countDown();

                                // complete successfully when all buckets are inserted.
                                if (latch.getCount() == 0) {
                                    handler.reply(null);
                                }
                            });
                        }).exceptionHandler(exception -> handler.fail(500, exception.getMessage()))
                        .end(bulkQuery(items, index, max, current));
            }
        });
    }

    /**
     * Determines the number of batches to bulk insert.
     * @param size the total number of items to insert
     * @return the number of batches required to submit the size
     */
    private int getBatchCount(int size) {
        return (size / MAX_BATCH) + ((size % MAX_BATCH != 0) ? 1 : 0);
    }

    /**
     * Builds a bulk query for insertion into elasticsearch
     * @param list full list of all elements
     * @param index the current item anchor
     * @param max max upper bound of items to include in the bulk
     * @param current lower bound of items to include in the bulk
     * @return a payload encoded as json-lines.
     */
    private String bulkQuery(JsonArray list, String index, int max, int current) {
        String query = "";
        JsonObject header = new JsonObject()
                .put("index", new JsonObject()
                        .put("_index", index)
                        .put("_type", "transactions"));

        for (int i = current; i < max; i++) {
            query += header.encode() + "\n";
            query += list.getJsonObject(i).encode() + "\n";
        }

        return query;
    }

    /**
     * Polls the elasticsearch server for version information. Sets connected if the server
     * is available.
     * @param id the id of the timer that triggered the request, not used.
     */
    private void pollElasticServer(Long id) {
        vertx.createHttpClient().get(Configuration.getElasticPort(), Configuration.getElasticHost(), "/",
                response -> response.bodyHandler(buffer -> {
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
        }).end();
    }

    public static String getElasticVersion() {
        return version;
    }

    public static boolean isConnected() {
        return connected;
    }
}
