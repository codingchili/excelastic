package com.codingchili.Model;

import io.vertx.core.json.JsonObject;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
    private static String DEFAULT_INDEX;
    private static int WEB_PORT;
    private static boolean SECURITY;
    private static String BASIC_AUTH;
    private static boolean ELASTIC_TLS;

    static {
        JsonObject configuration = getConfiguration();
        ELASTIC_PORT = configuration.getInteger("elastic_port", 9200);
        ELASTIC_HOST = configuration.getString("elastic_host", "localhost");
        WEB_PORT = configuration.getInteger("web_port", 9999);
        SECURITY = configuration.getBoolean("authentication", false);
        BASIC_AUTH = configuration.getString("basic", "username:password");
        ELASTIC_TLS = configuration.getBoolean("elastic_tls", false);
        DEFAULT_INDEX = configuration.getString("default_index", generateDefaultIndex());
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

    private static String generateDefaultIndex() {
        return DateTimeFormatter.ofPattern("MMMM-yyyy").format(ZonedDateTime.now()).toLowerCase();
    }

    /**
     * @return a basic auth encoded string of the configured username and password if
     * security is enabled in the configuration. Otherwise empty.
     */
    public static Optional<String> getBasicAuth()  {
        if (SECURITY) {
            return Optional.of(new String(Base64.getEncoder().encode(BASIC_AUTH.getBytes())));
        } else {
            return Optional.empty();
        }
    }

    /**
     * @return true if connections to elasticsearch should use TLS.
     */
    public static boolean isElasticTLS() {
        return ELASTIC_TLS;
    }

    /**
     * @return the port that elasticsearch is running on.
     */
    public static int getElasticPort() {
        return ELASTIC_PORT;
    }

    /**
     * @return the host of where elasticsearch is running.
     */
    public static String getElasticHost() {
        return ELASTIC_HOST;
    }

    /**
     * @return the port of the web interface.
     */
    public static int getWebPort() {
        return WEB_PORT;
    }

    public static void setWebPort(int webPort) {
        WEB_PORT = webPort;
    }

    /**
     * @return the local url to the website.
     */
    public static String getWebsiteURL() {
        return "http://localhost:" + getWebPort();
    }

    /**
     * @return the url to elasticsearch.
     */
    public static String getElasticURL() {
        String protocol = "http";
        if (ELASTIC_TLS) {
            protocol = "https";
        }
        return protocol + "://" + ELASTIC_HOST + ":" + ELASTIC_PORT;
    }

    /**
     * @return the default index to use for importing.
     */
    public static Object getDefaultIndex() {
        return DEFAULT_INDEX;
    }
}
