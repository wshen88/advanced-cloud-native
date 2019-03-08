package com.redhat.coolstore.gateway.verticle;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;

public class InventoryServiceVerticle extends AbstractVerticle {

    private WebClient webClient;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        String inventoryServiceHost = config().getString("inventory.service.host");
        int inventoryServicePort = config().getInteger("inventory.service.port");

        WebClientOptions options = new WebClientOptions()
                .setDefaultHost(inventoryServiceHost)
                .setDefaultPort(inventoryServicePort)
                .setMaxPoolSize(100)
                .setHttp2MaxPoolSize(100);
        webClient = WebClient.create(vertx, options);

        vertx.eventBus().<JsonObject>consumer("InventoryService", (msg) -> {
            JsonObject msgIn = msg.body();
            String action = msg.headers().get("action");
            if (action == null) {
                msg.fail(-1, "Action not specified");
            } else {
                switch (action) {
                    case "getInventory": {
                        String itemId = msgIn.getString("itemId");
                        webClient.get("/inventory/" + itemId + "?storeStatus=true").as(BodyCodec.jsonObject()).rxSend()
                                .subscribe(resp -> {
                                    if (resp.statusCode() == 404) {
                                        msg.reply(null);
                                    } else if (resp.statusCode() >= 400) {
                                        msg.fail(-1, "Inventory Service HTTP status code: " + resp.statusCode());
                                    } else {
                                        JsonObject body = resp.body();
                                        msg.reply(body);
                                    }
                                }, err -> msg.fail(-1, err.getMessage()));
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

    @Override
    public void stop() throws Exception {
        webClient.close();
    }
}
