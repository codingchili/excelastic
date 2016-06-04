package Model;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * @author Robin Duda
 */
public class ElasticWriter extends AbstractVerticle {
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
    }

    @Override
    public void start(Future<Void> start) {
        processTransactions();

        start.complete();
    }

    private void processTransactions() {
        vertx.eventBus().consumer(Configuration.BUS_TRANSACTIONS, handler -> {
            JsonObject data = (JsonObject) handler.body();
            String index = data.getString("index");

            vertx.createHttpClient().post(
                    Configuration.ELASTIC_PORT, Configuration.ELASTIC_HOST, index + "/_bulk")
                    .handler(response -> response.bodyHandler(body -> {
                    }))
                    .end(bulkQuery(data.getJsonArray("items"), index));
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
}
