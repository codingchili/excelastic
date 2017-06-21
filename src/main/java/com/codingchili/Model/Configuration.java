package com.codingchili.Model;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;

/**
 * @author Robin Duda
 *
 * Handles application configuration.
 */
public class Configuration {
    public static final String INDEXING_ELASTICSEARCH = "bus.transactions";
    private static final int ELASTIC_PORT = configuration().getInteger("elastic_port");
    private static final String ELASTIC_HOST = configuration().getString("elastic_host");
    private static int WEB_PORT = configuration().getInteger("web_port");
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

    public static int getElasticPort() {
        return ELASTIC_PORT;
    }

    public static String getElasticHost() {
        return ELASTIC_HOST;
    }

    public static int getWebPort() {
        return WEB_PORT;
    }

    public static void setWebPort(int webPort) {
        WEB_PORT = webPort;
    }

    public static String getWebsiteURL() {
        return "http://localhost:" + getWebPort();
    }

    public static String getElasticURL() {
        return "http://" + ELASTIC_HOST + ":" + ELASTIC_PORT;
    }
}
