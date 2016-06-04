package Controller;

import Model.Configuration;
import Model.FileParser;
import Model.ParserException;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.Iterator;

/**
 * @author Robin Duda
 */
public class Website extends AbstractVerticle {
    private Vertx vertx;

    @Override
    public void init(Vertx vertx, Context context) {
        this.vertx = vertx;
    }

    @Override
    public void start(Future<Void> start) {
        Router router = Router.router(vertx);

        router.route().handler(BodyHandler.create());
        setRouterAPI(router);
        router.route("/*").handler(StaticHandler.create());

        vertx.createHttpServer().requestHandler(router::accept).listen(Configuration.WEB_PORT);

        start.complete();
    }

    private void setRouterAPI(Router router) {
        router.route("/api/upload").handler(context -> {
            Iterator<FileUpload> iterator = context.fileUploads().iterator();

            if (iterator.hasNext()) {
                FileUpload upload = context.fileUploads().iterator().next();

                vertx.fileSystem().readFile(upload.uploadedFileName(), file -> {
                    try {
                        parse(file.result(), context.request().params());
                        redirect(context, "/done.html");
                    } catch (ParserException e) {
                        redirect(context, "/error.html");
                    }
                });
            } else {
                redirect(context, "/error.html");
            }
        });
    }

    private void parse(Buffer buffer, MultiMap params) throws ParserException {
        try {
            int columnRow = Integer.parseInt(params.get("offset"));
            String index = params.get("index");
            FileParser parser = new FileParser(buffer.getBytes(), columnRow);

            JsonObject result = new JsonObject()
                    .put("items", parser.toJsonArray())
                    .put("index", index.toLowerCase());

            sendOutput(result);
        } catch (NumberFormatException e) {
            throw new ParserException();
        }
    }

    private void sendOutput(JsonObject data) {
        vertx.eventBus().send(Configuration.BUS_TRANSACTIONS, data);
    }

    private void redirect(RoutingContext context, String uri) {
        context.request().response().setStatusCode(301).putHeader("location", uri).end();
    }
}
