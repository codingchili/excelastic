package com.codingchili;

import com.codingchili.Controller.Website;
import com.codingchili.Model.Configuration;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
    public Timeout timeout = Timeout.seconds(5);

    @Test
    public void shouldGetStartPage(TestContext context) {
        Async async = context.async();

        vertx.createHttpClient().getNow(Configuration.getWebPort(), "localhost", "/", response -> {
            context.assertEquals(200, response.statusCode());
            async.complete();
        });
    }

    @Ignore("The file must be recognized as a file on the server side, test broken.")
    public void shouldSucceedUpload(TestContext context) throws IOException {
        Async async = context.async();

        vertx.createHttpClient().post(Configuration.getWebPort(), "localhost", "/api/upload", response -> {
            response.bodyHandler(body -> {
                context.assertTrue(body.toString().contains("Done"));
                context.assertEquals(200, response.statusCode());
                async.complete();
            });
        }).putHeader("content-type", "multipart/form-data").end(new JsonObject()
                .put("index", "test")
                .put("offset", 5)
                .put("file", getFileBytes())
                .encode());
    }

    private byte[] getFileBytes() throws IOException {
        return Files.readAllBytes(Paths.get("src/test/java/test.xlsx"));
    }

    @Test
    public void shouldFailUpload(TestContext context) {
        Async async = context.async();

        vertx.createHttpClient().post(Configuration.getWebPort(), "localhost", "/api/upload", response -> {
            response.bodyHandler(body -> {
                context.assertTrue(body.toString().contains("error"));
                context.assertEquals(200, response.statusCode());
                async.complete();
            });
        }).end();
    }

}
