package com.codingchili.Model;

import java.util.logging.Logger;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * @author Robin Duda
 *         <p>
 *         Writes json data to elasticsearch for indexing.
 */
public class ElasticWriter extends AbstractVerticle {
    private static final String INDEX = "index";
    private static final String BULK = "/_bulk";
    private static final String ITEMS = "items";
    private static String version = "";
    private static final int poll = 5000;
    private boolean connected = false;
    private Logger logger = Logger.getLogger(getClass().getName());
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;

        vertx.setPeriodic(poll, this::pollElasticServer);
    }

    @Override
    public void start(Future<Void> start) {
        startSubmitListener();
        logger.info("Started elastic writer");
        start.complete();
    }


    private void startSubmitListener() {
        vertx.eventBus().consumer(Configuration.INDEXING_ELASTICSEARCH, handler -> {
            logger.info("Received file object, starting import..");
            JsonObject data = (JsonObject) handler.body();
            String index = data.getString(INDEX);

            vertx.createHttpClient().post(
                    Configuration.getElasticPort(), Configuration.getElasticHost(), index + BULK)
                    .handler(response -> {
                        logger.info(String.format("Submitted with result [%d] %s",
                                response.statusCode(),
                                response.statusMessage()));

                        response.bodyHandler(body -> {
                            handler.reply(body.toJsonObject());
                        });
                    })
                    .end(bulkQuery(data.getJsonArray(ITEMS), index));
        });
    }

    private String bulkQuery(JsonArray list, String index) {
        String query = "";
        JsonObject header = new JsonObject()
                .put("index", new JsonObject()
                        .put("_index", index)
                        .put("_type", "transactions"));

        for (int i = 0; i < list.size(); i++) {
            query += header.encode() + "\n";
            query += list.getJsonObject(i).encode() + "\n";
        }

        return query;
    }

    private void pollElasticServer(Long id) {
        vertx.createHttpClient().get(Configuration.getElasticPort(), Configuration.getElasticHost(), "/",
                response -> response.bodyHandler(buffer -> {
                       version = buffer.toJsonObject().getJsonObject("version").getString("number");
                        if (!connected) {
                            logger.info(String.format("Connected to elasticsearch server %s", version));
                            connected = true;
                        }
                    })).exceptionHandler(event -> {
                    connected = false;
                    logger.severe(event.getMessage());
        }).end();
    }

    public static String getElasticVersion() {
        return version;
    }
}
