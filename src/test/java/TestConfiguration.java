import com.codingchili.Model.Configuration;
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
        context.assertNotNull(Configuration.WEB_PORT);
        context.assertNotNull(Configuration.ELASTIC_PORT);
        context.assertNotNull(Configuration.ELASTIC_HOST);
    }

}
