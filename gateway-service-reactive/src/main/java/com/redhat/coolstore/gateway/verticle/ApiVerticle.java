package com.redhat.coolstore.gateway.verticle;

import java.util.Optional;

import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.Status;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.healthchecks.HealthCheckHandler;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;

public class ApiVerticle extends AbstractVerticle {

    @Override
    public void start(io.vertx.core.Future<Void> startFuture) throws Exception {

        Router router = Router.router(vertx);

        router.get("/api/cart/:cartId").handler(this::getCart);
        router.post("/api/cart/:cartId/:itemId/:quantity").handler(this::addToCart);
        router.delete("/api/cart/:cartId/:itemId/:quantity").handler(this::removeFromCart);
        router.post("/api/cart/checkout/:cartId").handler(this::checkoutCart);
        router.get("/api/products").handler(this::getProducts);
        router.route("/api/*").failureHandler(rc -> rc.response().setStatusCode(500).end());

        //Health checks
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx)
                .register("health", f -> f.complete(Status.OK()));
        router.get("/health").handler(healthCheckHandler);

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .rxListen(config().getInteger("gateway.http.port", 8080))
                .toCompletable()
                .subscribe(CompletableHelper.toObserver(startFuture));
    }

    private void getCart(RoutingContext rc) {
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "getCart");
        JsonObject msg = new JsonObject().put("cartId", rc.request().getParam("cartId"));
        vertx.eventBus().<JsonObject>rxSend("CartService", msg, options)
                .map(Message::body)
                .subscribe(json -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                        .end(json.encode()), rc::fail);
    }

    private void addToCart(RoutingContext rc) {
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "addToCart");
        JsonObject msg = new JsonObject().put("cartId", rc.request().getParam("cartId"))
                .put("itemId", rc.request().getParam("itemId"))
                .put("quantity", stringToInt(rc.request().getParam("quantity")).orElse(0));
        vertx.eventBus().<JsonObject>rxSend("CartService", msg, options)
                .map(Message::body)
                .subscribe(json -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                        .end(json.encode()), rc::fail);
    }

    private void removeFromCart(RoutingContext rc) {
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "removeFromCart");
        JsonObject msg = new JsonObject().put("cartId", rc.request().getParam("cartId"))
                .put("itemId", rc.request().getParam("itemId"))
                .put("quantity", stringToInt(rc.request().getParam("quantity")).orElse(0));
        vertx.eventBus().<JsonObject>rxSend("CartService", msg, options)
                .map(Message::body)
                .subscribe(json -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                   .end(json.encode()), rc::fail);
    }

    private void checkoutCart(RoutingContext rc) {
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "checkoutCart");
        JsonObject msg = new JsonObject().put("cartId", rc.request().getParam("cartId"));
        vertx.eventBus().<JsonObject>rxSend("CartService", msg, options)
                .map(Message::body)
                .subscribe(json -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                    .end(json.encode()), rc::fail);
    }

    private void getProducts(RoutingContext rc) {
        DeliveryOptions options = new DeliveryOptions().addHeader("action", "getProducts");
        JsonObject msg = new JsonObject();
        vertx.eventBus().<JsonArray>rxSend("CatalogService", msg, options)
                .map(Message::body)
                .subscribe(json -> rc.response().putHeader(HttpHeaders.CONTENT_TYPE.toString(), "application/json")
                    .end(json.encode()), rc::fail);
    }

    private static Optional<Integer> stringToInt(String s) {
        try {
            return Optional.of(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

}
