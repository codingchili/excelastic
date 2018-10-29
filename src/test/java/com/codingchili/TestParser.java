package com.codingchili;

import com.codingchili.Model.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.*;

/**
 * @author Robin Duda
 */
@RunWith(VertxUnitRunner.class)
public class TestParser {
    static final int ROW_OFFSET = 5;
    static final String TEST_XLS_FILE = "/test.xls";

    private static final String TEST_XLSX_FILE = "/test.xlsx";
    private static final String TEST_INVALID_FILE = "/invalid.xlsx";
    private static final String TEST_CSV = "/test.csv";
    private static final String BIG_CSV = "/big.csv";
    private static final String XLSX = ".xlsx";

    @Test
    public void failParseInvalid() throws Exception {
        try {
            new ExcelParser().setFileData(toPath(TEST_INVALID_FILE), 5, XLSX);
            throw new Exception("Should fail for invalid bytes.");
        } catch (ParserException ignored) {
        }
    }

    @Test(expected = InvalidFileNameException.class)
    public void testParseMissingExt() {
        ParserFactory.getByFilename("file");
    }

    @Test(expected = UnsupportedFileTypeException.class)
    public void testParseMissingParser() {
        ParserFactory.getByFilename("file.xxx");
    }

    @Test
    public void testParseOOXML(TestContext context) throws IOException {
        testParseFile(context, TEST_XLSX_FILE);
    }

    @Test
    public void testParse2007(TestContext context) throws IOException {
        testParseFile(context, TEST_XLS_FILE);
    }

    @Test
    public void testParseCSV(TestContext context) throws IOException {
        testParseFile(context, TEST_CSV);
    }

    @Test
    @Ignore("")
    public void testParseBig(TestContext context) throws IOException {
        CSVParser.setMaxMapSize(32); // has the same effect as using a large file.
        try {
            testParseFile(context, TEST_CSV);
        } finally {
            CSVParser.setMaxMapSize(Integer.MAX_VALUE);
        }
    }


    @Test
    @Ignore("Run when you need some testing data.")
    public void generateMonsterCSV() throws IOException {
        File file = new File(getClass().getResource("").getPath() + BIG_CSV);
        final int ITEMS = 24000;
        final int COLUMNS = 99;


        try (FileOutputStream out = new FileOutputStream(file)) {
            for (int r = 0; r < ITEMS; r++) {
                for (int i = 0; i < COLUMNS; i++) {
                    if (r == 0) {
                        out.write(("title " + i + ",").getBytes());
                    } else {
                        out.write(("value" + i + ",").getBytes());
                    }
                }
                out.write("end".getBytes());

                if (r != ITEMS - 1) {
                    out.write("\n".getBytes());
                }

                if (r % 100 == 0) {
                    System.out.println("process " + r + "/" + ITEMS + " ..");
                }
            }
        }

    }

    private void testParseFile(TestContext context, String fileName) throws IOException, ParserException {
        FileParser parser = ParserFactory.getByFilename(fileName);
        parser.setFileData(
                toPath(fileName),
                ROW_OFFSET,
                fileName
        );

        parser.initialize();

        parser.subscribe(new Subscriber<JsonObject>() {
            JsonArray list = new JsonArray();


            @Override
            public void onSubscribe(Subscription subscription) {
                subscription.request(3);
            }

            @Override
            public void onNext(JsonObject entry) {
                list.add(entry);
            }

            @Override
            public void onError(Throwable throwable) {

            }

            @Override
            public void onComplete() {
                context.assertEquals(2, list.size());

                for (int i = 0; i < list.size(); i++) {
                    JsonObject json = list.getJsonObject(i);
                    context.assertTrue(json.containsKey("Column 1"));
                    context.assertTrue(json.containsKey("Column 2"));
                    context.assertTrue(json.containsKey("Column 3"));

                    context.assertEquals("cell " + (ROW_OFFSET + 1 + i) + "." + 1, json.getString("Column 1"));
                    context.assertEquals("cell " + (ROW_OFFSET + 1 + i) + "." + 2, json.getString("Column 2"));
                    context.assertEquals("cell " + (ROW_OFFSET + 1 + i) + "." + 3, json.getString("Column 3"));
                }
                parser.free();
            }
        });
    }

    private static String toPath(String resource) {
        return TestParser.class.getResource(resource).getPath();
    }
}
