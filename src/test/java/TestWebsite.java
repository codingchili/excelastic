import Controller.Website;
import Model.Configuration;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.*;
import org.junit.runner.RunWith;

/**
 * @author Robin Duda
 */
@RunWith(VertxUnitRunner.class)
public class TestWebsite {
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.deployVerticle(new Website(), context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Rule
    public Timeout timeout = Timeout.seconds(2);

    @Test
    public void shouldGetStartPage(TestContext context) {
        Async async = context.async();

        vertx.createHttpClient().getNow(Configuration.WEB_PORT, "localhost", "/", response -> {
            context.assertEquals(200, response.statusCode());
            async.complete();
        });
    }

    @Ignore("Requires correct XLSX file to be posted.")
    public void shouldSucceedUpload(TestContext context) {
        Async async = context.async();

        vertx.createHttpClient().post(Configuration.WEB_PORT, "localhost", "/api/upload", response -> {
            context.assertEquals(301, response.statusCode());
            context.assertEquals("/done.html", response.getHeader("location"));
            async.complete();
        }).end();
    }

    @Test
    public void shouldFailUpload(TestContext context) {
        Async async = context.async();

        vertx.createHttpClient().post(Configuration.WEB_PORT, "localhost", "/api/upload", response -> {
            context.assertEquals(301, response.statusCode());
            context.assertEquals("/error.html", response.getHeader("location"));
            async.complete();
        }).end();
    }

}
