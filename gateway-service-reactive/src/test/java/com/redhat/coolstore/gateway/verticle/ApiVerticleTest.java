package com.redhat.coolstore.gateway.verticle;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.net.ServerSocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class ApiVerticleTest {

    private Vertx vertx;

    private int port;

    @Before
    public void setUp(TestContext context) throws Exception {
        vertx = Vertx.vertx();
        vertx.exceptionHandler(context.exceptionHandler());
        ServerSocket socket = new ServerSocket(0);
        port = socket.getLocalPort();
        socket.close();
        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("gateway.http.port", port));
        vertx.deployVerticle(new ApiVerticle(), options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void testGetCart(TestContext context) throws Exception {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("getCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            msg.reply(new JsonObject().put("result", "getCart"));
        });

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/cart/mycart", response -> {
            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.headers().get("Content-type"), equalTo("application/json"));
            response.bodyHandler(body -> {
                JsonObject result = body.toJsonObject();
                assertThat(result, notNullValue());
                assertThat(result.containsKey("result"), is(true));
                assertThat(result.getString("result"), equalTo("getCart"));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler());
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testGetCartWhenCartVerticleRespondsWithError(TestContext context) throws Exception {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("getCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            msg.fail(-1, "error");
        });

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/cart/mycart", response -> {
            assertThat(response.statusCode(), equalTo(500));
            async.complete();
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testAddToCart(TestContext context) {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("addToCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            assertThat(msg.body().getString("itemId"), equalTo("item"));
            assertThat(msg.body().getInteger("quantity"), equalTo(1));
            msg.reply(new JsonObject().put("result", "addToCart"));
        });

        Async async = context.async();
        vertx.createHttpClient().post(port, "localhost", "/api/cart/mycart/item/1", response -> {
            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.headers().get("Content-type"), equalTo("application/json"));
            response.bodyHandler(body -> {
                JsonObject result = body.toJsonObject();
                assertThat(result, notNullValue());
                assertThat(result.containsKey("result"), is(true));
                assertThat(result.getString("result"), equalTo("addToCart"));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler());
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testAddToCartWhenCartVerticleRespondsWithError(TestContext context) throws Exception {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("addToCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            assertThat(msg.body().getString("itemId"), equalTo("item"));
            assertThat(msg.body().getInteger("quantity"), equalTo(1));
            msg.fail(-1, "error");
        });

        Async async = context.async();
        vertx.createHttpClient().post(port, "localhost", "/api/cart/mycart/item/1", response -> {
            assertThat(response.statusCode(), equalTo(500));
            async.complete();
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testRemoveFromCart(TestContext context) {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("removeFromCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            assertThat(msg.body().getString("itemId"), equalTo("item"));
            assertThat(msg.body().getInteger("quantity"), equalTo(1));
            msg.reply(new JsonObject().put("result", "removeFromCart"));
        });

        Async async = context.async();
        vertx.createHttpClient().delete(port, "localhost", "/api/cart/mycart/item/1", response -> {
            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.headers().get("Content-type"), equalTo("application/json"));
            response.bodyHandler(body -> {
                JsonObject result = body.toJsonObject();
                assertThat(result, notNullValue());
                assertThat(result.containsKey("result"), is(true));
                assertThat(result.getString("result"), equalTo("removeFromCart"));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler());
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testRemoveFromCartWhenCartVerticleRespondsWithError(TestContext context) throws Exception {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("removeFromCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            assertThat(msg.body().getString("itemId"), equalTo("item"));
            assertThat(msg.body().getInteger("quantity"), equalTo(1));
            msg.fail(-1, "error");
        });

        Async async = context.async();
        vertx.createHttpClient().delete(port, "localhost", "/api/cart/mycart/item/1", response -> {
            assertThat(response.statusCode(), equalTo(500));
            async.complete();
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testCheckoutCart(TestContext context) {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("checkoutCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            msg.reply(new JsonObject().put("result", "checkoutCart"));
        });

        Async async = context.async();
        vertx.createHttpClient().post(port, "localhost", "/api/cart/checkout/mycart", response -> {
            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.headers().get("Content-type"), equalTo("application/json"));
            response.bodyHandler(body -> {
                JsonObject result = body.toJsonObject();
                assertThat(result, notNullValue());
                assertThat(result.containsKey("result"), is(true));
                assertThat(result.getString("result"), equalTo("checkoutCart"));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler());
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testCheckoutCartWhenCartVerticleRespondsWithError(TestContext context) throws Exception {
        vertx.eventBus().<JsonObject>consumer("CartService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("checkoutCart"));
            assertThat(msg.body().getString("cartId"), equalTo("mycart"));
            msg.fail(-1, "error");
        });

        Async async = context.async();
        vertx.createHttpClient().post(port, "localhost", "/api/cart/checkout/mycart", response -> {
            assertThat(response.statusCode(), equalTo(500));
            async.complete();
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testGetProducts(TestContext context) {
        vertx.eventBus().<JsonObject>consumer("CatalogService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("getProducts"));
            msg.reply(new JsonArray().add(new JsonObject().put("result", "getProducts")));
        });

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/products", response -> {
            assertThat(response.statusCode(), equalTo(200));
            assertThat(response.headers().get("Content-type"), equalTo("application/json"));
            response.bodyHandler(body -> {
                JsonArray result = body.toJsonArray();
                assertThat(result, notNullValue());
                assertThat(result.size(), equalTo(1));
                assertThat(result.getJsonObject(0).getString("result"), equalTo("getProducts"));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler());
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testGetProductsWhenCatalogVerticleRespondsWithError(TestContext context) throws Exception {
        vertx.eventBus().<JsonObject>consumer("CatalogService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("getProducts"));
            msg.fail(-1, "error");
        });

        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/api/products", response -> {
            assertThat(response.statusCode(), equalTo(500));
            async.complete();
        })
        .exceptionHandler(context.exceptionHandler())
        .end();
    }

    @Test
    public void testReadinessHealthCheck(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient().get(port, "localhost", "/health", response -> {
                assertThat(response.statusCode(), equalTo(200));
                async.complete();
            })
            .exceptionHandler(context.exceptionHandler())
            .end();
    }

}
