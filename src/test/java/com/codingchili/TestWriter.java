package com.codingchili;

import com.codingchili.Model.*;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author Robin Duda
 */
@RunWith(VertxUnitRunner.class)
public class TestWriter {
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        ImportEventCodec.registerOn(vertx);
        vertx.deployVerticle(new ElasticWriter(), context.asyncAssertSuccess());
    }

    @Rule
    public Timeout timeout = Timeout.seconds(5);

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void shouldWriteToElasticPort(TestContext context) throws IOException {
        Async async = context.async();

        vertx.createHttpServer().requestHandler(request -> {

            request.bodyHandler(body -> {
                context.assertTrue(body.toString() != null);
                async.complete();
            });
        }).listen(Configuration.getElasticPort());

        ExcelParser fileParser = new ExcelParser();
        fileParser.setFileData(getClass().getResource(TestParser.TEST_XLS_FILE).getPath(),
                TestParser.ROW_OFFSET,
                "testFileName.xls");

        vertx.eventBus().send(Configuration.INDEXING_ELASTICSEARCH, new ImportEvent()
                .setParser(fileParser)
                .setIndex("text-index")
                .setClearExisting(false)
                .setMapping("test-mapping"));
    }

}
