package com.redhat.coolstore.gateway.verticle;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.client.WebClient;
import io.vertx.reactivex.ext.web.codec.BodyCodec;

public class CatalogServiceVerticle extends AbstractVerticle {

    private WebClient webClient;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        String catalogServiceHost = config().getString("catalog.service.host");
        int catalogServicePort = config().getInteger("catalog.service.port");
        WebClientOptions options = new WebClientOptions()
                .setDefaultHost(catalogServiceHost)
                .setDefaultPort(catalogServicePort)
                .setHttp2MaxPoolSize(100)
                .setMaxPoolSize(100);
        webClient = WebClient.create(vertx, options);

        vertx.eventBus().<JsonObject>consumer("CatalogService", (msg) -> {
            String action = msg.headers().get("action");
            if (action == null) {
                msg.fail(-1, "Action not specified");
            } else {
                switch (action) {
                    case "getProducts": {
                        webClient.get("/products").as(BodyCodec.jsonArray()).rxSend()
                            .map(resp -> {
                                if (resp.statusCode() > 200) {
                                    msg.fail(-1, "Catalog Service HTTP status code: " + resp.statusCode());
                                }
                                return resp.body();
                            })
                            .flatMap(products ->
                                Observable.fromIterable(products).cast(JsonObject.class)
                                    .flatMapSingle(product -> {
                                        JsonObject inventoryMsg = new JsonObject().put("itemId", product.getString("itemId"));
                                        DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("action", "getInventory");
                                        return vertx.eventBus().<JsonObject>rxSend("InventoryService", inventoryMsg, deliveryOptions)
                                            .map(m -> product.copy().put("availability", m.body())).onErrorReturnItem(product);
                                    }).toList()
                            ).subscribe(list -> msg.reply(new JsonArray(list)), err -> msg.fail(-1, err.getMessage()));
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

    @SuppressWarnings("rawtypes")
    private void handleResponse(AsyncResult<HttpResponse<JsonArray>> ar, Message<JsonObject> msg) {
        if (ar.failed()) {
            msg.fail(-1, ar.cause().getMessage());
        } else {
            HttpResponse<JsonArray> resp = ar.result();
            if (resp.statusCode() >= 400) {
                msg.fail(-1, "Catalog Service HTTP status code: " + resp.statusCode());
            } else {
                List<Future> futures = new ArrayList<>();
                JsonArray body = resp.body();
                body.stream().map(o -> (JsonObject) o).forEach(obj -> {
                    Future<Void> f = Future.future();
                    futures.add(f);
                    JsonObject getInventoryMsg = new JsonObject().put("itemId", obj.getString("itemId"));
                    DeliveryOptions options = new DeliveryOptions();
                    options.addHeader("action", "getInventory");
                    vertx.eventBus().<JsonObject>send("InventoryService", getInventoryMsg, options, ar1 -> {
                        if (ar1.succeeded()) {
                            JsonObject inventory = ar1.result().body();
                            obj.put("availability", inventory);
                        }
                        f.complete();
                    });
                });
                CompositeFuture.all(futures).setHandler(ar2 -> {
                    msg.reply(body);
                });
            }
        }
    }



}
