import com.codingchili.Model.FileParser;
import com.codingchili.Model.ParserException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static com.codingchili.Model.FileParser.ITEMS;

/**
 * @author Robin Duda
 */
@RunWith(VertxUnitRunner.class)
public class TestParser {
    private static final int ROW_OFFSET = 5;
    private static final String XLSX = ".xlsx";

    @Test
    public void failParseInvalid() throws Exception {
        try {
            new FileParser(new byte[2048], 5, XLSX);
            throw new Exception("Should fail for invalid bytes.");
        } catch (ParserException ignored) {
        }
    }

    @Test
    public void testParseOOXML(TestContext context) throws IOException, ParserException {
        testParseFile(context, "src/test/java/test.xlsx");
    }

    @Test
    public void testParse2007(TestContext context) throws IOException, ParserException {
        testParseFile(context, "src/test/java/test.xls");
    }

    private void testParseFile(TestContext context, String fileName) throws IOException, ParserException {
        FileParser parser = new FileParser(Files.readAllBytes(Paths.get(fileName)), ROW_OFFSET, fileName);
        JsonArray list = parser.toImportable("index").getJsonArray(ITEMS);

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
    }
}
