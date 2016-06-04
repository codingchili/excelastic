package Model;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;

/**
 * @author Robin Duda
 */
public class Configuration {
    public static final int ELASTIC_PORT = configuration().getInteger("elastic_port");
    public static final String ELASTIC_HOST = configuration().getString("elastic_host");
    public static final int WEB_PORT = configuration().getInteger("web_port");
    public static final String BUS_TRANSACTIONS = "bus.transactions";
    private static JsonObject configuration;

    private static JsonObject configuration() {
        try {
            if (configuration == null) {
                configuration = new JsonObject(
                        new String(Files.readAllBytes(FileSystems.getDefault().getPath("configuration.json"))));
            }
            return configuration;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
