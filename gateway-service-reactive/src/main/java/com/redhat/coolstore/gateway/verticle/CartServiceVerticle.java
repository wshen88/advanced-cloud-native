package com.redhat.coolstore.gateway.verticle;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.eventbus.Message;
import io.vertx.reactivex.ext.web.client.HttpResponse;
import io.vertx.reactivex.ext.web.codec.BodyCodec;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;

public class CartServiceVerticle extends AbstractVerticle {

    private WebClient webClient;

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        String cartServiceHost = config().getString("cart.service.host");
        int cartServicePort = config().getInteger("cart.service.port");
        WebClientOptions options = new WebClientOptions()
                .setDefaultHost(cartServiceHost)
                .setDefaultPort(cartServicePort)
                .setMaxPoolSize(100)
                .setHttp2MaxPoolSize(100);
        webClient = WebClient.create(vertx, options);

        vertx.eventBus().<JsonObject>consumer("CartService", (msg) -> {

            JsonObject msgIn = msg.body();
            String action = msg.headers().get("action");
            if (action == null) {
                msg.fail(-1, "Action not specified");
            } else {
                switch (action) {
                    case "getCart": {
                        String cartId = msgIn.getString("cartId");
                        webClient.get("/cart/" + cartId).as(BodyCodec.jsonObject()).rxSend()
                                .subscribe(resp -> handleResponse(resp, msg), err -> msg.fail(-1, err.getMessage()));
                        break;
                    }
                    case "addToCart": {
                        String cartId = msgIn.getString("cartId");
                        String itemId = msgIn.getString("itemId");
                        int quantity = msgIn.getInteger("quantity");
                        String uri = new StringBuilder().append("/cart/")
                                .append(cartId).append("/")
                                .append(itemId).append("/")
                                .append(Integer.toString(quantity)).toString();
                        webClient.post(uri).as(BodyCodec.jsonObject()).rxSend()
                                .subscribe(resp -> handleResponse(resp, msg), err -> msg.fail(-1, err.getMessage()));
                        break;
                    }
                    case "removeFromCart": {
                        String cartId = msgIn.getString("cartId");
                        String itemId = msgIn.getString("itemId");
                        int quantity = msgIn.getInteger("quantity");
                        String uri = new StringBuilder().append("/cart/")
                                .append(cartId).append("/")
                                .append(itemId).append("/")
                                .append(Integer.toString(quantity)).toString();
                        webClient.delete(uri).as(BodyCodec.jsonObject()).rxSend()
                                .subscribe(resp -> handleResponse(resp, msg), err -> msg.fail(-1, err.getMessage()));
                        break;
                    }
                    case "checkoutCart": {
                        String cartId = msgIn.getString("cartId");
                        webClient.post("/cart/checkout/" + cartId).as(BodyCodec.jsonObject()).rxSend()
                                .subscribe(resp -> handleResponse(resp, msg), err -> msg.fail(-1, err.getMessage()));
                        break;
                    }
                    default: {
                        msg.fail(-1, "Invalid action " + action);
                    }
                }
            }

        });

        startFuture.complete();
    }

    private void handleResponse(HttpResponse<JsonObject> resp, Message<JsonObject> msg) {
        if (resp.statusCode() >= 400) {
            msg.fail(-1, "Cart Service HTTP status code: " + resp.statusCode());
        } else {
            JsonObject body = resp.body();
            msg.reply(body);
        }
    }

    @Override
    public void stop() throws Exception {
        webClient.close();
    }
}
