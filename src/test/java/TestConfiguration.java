import Model.Configuration;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * @author Robin Duda
 */

@RunWith(VertxUnitRunner.class)
public class TestConfiguration {

    @Test
    public void shouldLoadConfiguration(TestContext context) {
        context.assertEquals(Configuration.WEB_PORT, 8080);
        context.assertEquals(Configuration.ELASTIC_INDEX, "transactions_v4");
        context.assertEquals(Configuration.ELASTIC_PORT, 9200);
        context.assertEquals(Configuration.ROW_OFFSET, 5);
    }

}
