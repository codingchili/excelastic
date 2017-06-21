import com.codingchili.Model.Configuration;
import com.codingchili.Model.ElasticWriter;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Robin Duda
 */
@RunWith(VertxUnitRunner.class)
public class TestWriter {
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.deployVerticle(new ElasticWriter(), context.asyncAssertSuccess());
    }

    @Rule
    public Timeout timeout = Timeout.seconds(5);

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void shouldWriteToElasticPort(TestContext context) {
        Async async = context.async();

        vertx.createHttpServer().requestHandler(request -> {

            request.bodyHandler(body -> {
                context.assertTrue(body.toString() != null);
                async.complete();
            });
        }).listen(Configuration.getElasticPort());

        vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH, new JsonObject()
                .put("items", new JsonArray().add(new JsonObject().put("test", true)))
                .put("index", "test-index"));
    }

}
