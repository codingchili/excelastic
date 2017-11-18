package com.codingchili.Model;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * @author Robin Duda
 *
 * Handles application configuration.
 */
public class Configuration {
    public static final String INDEXING_ELASTICSEARCH = "bus.transactions";
    private static final String CONFIGURATION_JSON = "configuration.json";
    private static int ELASTIC_PORT;
    private static String ELASTIC_HOST;
    private static int WEB_PORT;
    private static boolean SECURITY;
    private static String BASIC_AUTH;

    static {
        JsonObject configuration = getConfiguration();
        ELASTIC_PORT = configuration.getInteger("elastic_port", 9200);
        ELASTIC_HOST = configuration.getString("elastic_host", "localhost");
        WEB_PORT = configuration.getInteger("web_port", 9999);
        SECURITY = configuration.getBoolean("authentication", false);
        BASIC_AUTH = configuration.getString("basic", "username:password");

    }

    private static JsonObject getConfiguration() {
        try {
            return new JsonObject(
                        new String(Files.readAllBytes(FileSystems.getDefault().getPath(CONFIGURATION_JSON))));
        } catch (IOException e) {
            Logger.getLogger(Configuration.class.getName()).info(
                    String.format("Configuration file %s is not present, using defaults.", CONFIGURATION_JSON));
            return new JsonObject();
        }
    }

    public static Optional<String> getBasicAuth()  {
        if (SECURITY) {
            return Optional.of(new String(Base64.getEncoder().encode(BASIC_AUTH.getBytes())));
        } else {
            return Optional.empty();
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
