package Model;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
        createIndex();
        processTransactions();

        start.complete();
    }

    private void createIndex() {
        vertx.createHttpClient().put(
                Configuration.ELASTIC_PORT, "localhost",
                Configuration.ELASTIC_INDEX + "/all/").handler(response -> {

        }).end(template().encode());
    }

    private JsonObject template() {
        return new JsonObject()
                .put("template", new JsonObject()
                        .put("mappings", new JsonObject()
                                .put(Configuration.ELASTIC_INDEX, new JsonObject()
                                        .put("properties", new JsonObject()
                                                .put("Valutadatum", new JsonObject()
                                                        .put("type", "date"))))));
    }

    private void processTransactions() {
        vertx.eventBus().consumer(Configuration.BUS_TRANSACTIONS, handler -> {
            JsonObject data = (JsonObject) handler.body();

            vertx.createHttpClient().post(
                    Configuration.ELASTIC_PORT, "localhost",
                    Configuration.ELASTIC_INDEX + "/all/").handler(response -> {

            }).end(data.encode());
        });
    }
}
