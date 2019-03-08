package com.redhat.coolstore.gateway.verticle;

import io.reactivex.Completable;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.config.ConfigRetriever;
import io.vertx.reactivex.core.AbstractVerticle;

public class MainVerticle extends AbstractVerticle {

    Logger log = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> startFuture) throws Exception {

        ConfigStoreOptions jsonConfigStore = new ConfigStoreOptions().setType("json");
        ConfigStoreOptions appStore = new ConfigStoreOptions()
                .setType("configmap")
                .setFormat("yaml")
                .setConfig(new JsonObject()
                        .put("name", System.getenv("APP_CONFIGMAP_NAME"))
                        .put("key", System.getenv("APP_CONFIGMAP_KEY")));

        ConfigRetrieverOptions options = new ConfigRetrieverOptions();
        if (System.getenv("KUBERNETES_NAMESPACE") != null) {
            //we're running in Kubernetes
            options.addStore(appStore);
        } else {
            //default to json based config
            jsonConfigStore.setConfig(config());
            options.addStore(jsonConfigStore);
        }

        ConfigRetriever.create(vertx, options).rxGetConfig().flatMapCompletable(this::deployVerticles)
                .subscribe(CompletableHelper.toObserver(startFuture));
    }

    private Completable deployVerticles(JsonObject config) {

        DeploymentOptions options = new DeploymentOptions();
        options.setConfig(config);
        return vertx.rxDeployVerticle(ApiVerticle.class.getName(), options).toCompletable()
                .andThen(vertx.rxDeployVerticle(CartServiceVerticle.class.getName(), options)).toCompletable()
                .andThen(vertx.rxDeployVerticle(CatalogServiceVerticle.class.getName(), options)).toCompletable()
                .andThen(vertx.rxDeployVerticle(InventoryServiceVerticle.class.getName(), options)).toCompletable();
    }
}
