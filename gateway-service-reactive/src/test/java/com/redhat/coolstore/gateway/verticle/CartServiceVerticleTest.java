package com.redhat.coolstore.gateway.verticle;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CartServiceVerticleTest {

    private Vertx vertx;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Before
    public void setUp(TestContext context) throws IOException {
      vertx = Vertx.vertx();

      // Register the context exception handler
      vertx.exceptionHandler(context.exceptionHandler());

      JsonObject config = new JsonObject()
          .put("cart.service.host", "localhost")
          .put("cart.service.port", wireMockRule.port());
      DeploymentOptions options = new DeploymentOptions().setConfig(config);

      // We pass the options as the second parameter of the deployVerticle method.
      vertx.deployVerticle(new CartServiceVerticle(), options, context.asyncAssertSuccess());
    }

    @Test
    public void testGetCart(TestContext context) throws Exception {
        stubFor(get(urlEqualTo("/cart/mycart"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json")
                    .withBody(new JsonObject().put("id", "mycart").encode())));

        JsonObject msgSent = new JsonObject()
            .put("cartId", "mycart");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            assertThat(ar.result().body().getString("id"), equalTo("mycart"));
            wireMockRule.verify(getRequestedFor(urlEqualTo("/cart/mycart")));
            async.complete();
        });
    }

    @Test
    public void testGetCartWhenCartServiceThrowsError(TestContext context) throws Exception {

        stubFor(get(urlEqualTo("/cart/mycart"))
                .willReturn(
                aResponse().withStatus(500)));

        JsonObject msgSent = new JsonObject()
            .put("cartId", "mycart");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, equalTo("Cart Service HTTP status code: 500"));
            wireMockRule.verify(getRequestedFor(urlEqualTo("/cart/mycart")));
            async.complete();
        });
    }

    @Test
    public void testGetCartWhenCartServiceIsDown(TestContext context) throws Exception {

        wireMockRule.shutdownServer();
        Awaitility.await().atMost(Duration.ONE_SECOND).until(() -> !wireMockRule.isRunning());

        JsonObject msgSent = new JsonObject()
            .put("cartId", "mycart");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, CoreMatchers.startsWith("Connection refused:"));
            async.complete();
        });
    }

    @Test
    public void testAddToCart(TestContext context) throws Exception {
        String response = "{\"cartId\" : \"mycart\"}";

        stubFor(post(urlEqualTo("/cart/mycart/item/10"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json").withBody(response)));

        JsonObject msgSent = new JsonObject()
            .put("cartId", "mycart")
            .put("itemId", "item")
            .put("quantity", 10);
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "addToCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            JsonObject result = ar.result().body();
            assertThat(result.containsKey("cartId"), is(true));
            assertThat(result.getString("cartId"), equalTo("mycart"));
            wireMockRule.verify(postRequestedFor(urlEqualTo("/cart/mycart/item/10")));
            async.complete();
        });
    }

    @Test
    public void testAddToCartWhenCartServiceThrowsError(TestContext context) throws Exception {

        stubFor(post(urlEqualTo("/cart/mycart/item/10"))
                .willReturn(
                aResponse().withStatus(500)));

        JsonObject msgSent = new JsonObject()
                .put("cartId", "mycart")
                .put("itemId", "item")
                .put("quantity", 10);
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "addToCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, equalTo("Cart Service HTTP status code: 500"));
            wireMockRule.verify(postRequestedFor(urlEqualTo("/cart/mycart/item/10")));
            async.complete();
        });
    }

    @Test
    public void testAddToCartWhenCartServiceIsDown(TestContext context) throws Exception {

        wireMockRule.shutdownServer();
        Awaitility.await().atMost(Duration.ONE_SECOND).until(() -> !wireMockRule.isRunning());

        JsonObject msgSent = new JsonObject()
                .put("cartId", "mycart")
                .put("itemId", "item")
                .put("quantity", 10);
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "addToCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, CoreMatchers.startsWith("Connection refused:"));
            async.complete();
        });
    }

    @Test
    public void testRemoveFromCart(TestContext context) throws Exception {
        String response = "{\"cartId\" : \"mycart\"}";

        stubFor(delete(urlEqualTo("/cart/mycart/item/10"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json").withBody(response)));

        JsonObject msgSent = new JsonObject()
            .put("cartId", "mycart")
            .put("itemId", "item")
            .put("quantity", 10);
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "removeFromCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            JsonObject result = ar.result().body();
            assertThat(result.containsKey("cartId"), is(true));
            assertThat(result.getString("cartId"), equalTo("mycart"));
            wireMockRule.verify(deleteRequestedFor(urlEqualTo("/cart/mycart/item/10")));
            async.complete();
        });
    }

    @Test
    public void testRemoveFromCartWhenCartServiceThrowsError(TestContext context) throws Exception {

        stubFor(delete(urlEqualTo("/cart/mycart/item/10"))
                .willReturn(
                aResponse().withStatus(500)));

        JsonObject msgSent = new JsonObject()
                .put("cartId", "mycart")
                .put("itemId", "item")
                .put("quantity", 10);
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "removeFromCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, equalTo("Cart Service HTTP status code: 500"));
            wireMockRule.verify(deleteRequestedFor(urlEqualTo("/cart/mycart/item/10")));
            async.complete();
        });
    }

    @Test
    public void testRemoveFromCartWhenCartServiceIsDown(TestContext context) throws Exception {

        wireMockRule.shutdownServer();
        Awaitility.await().atMost(Duration.ONE_SECOND).until(() -> !wireMockRule.isRunning());

        JsonObject msgSent = new JsonObject()
                .put("cartId", "mycart")
                .put("itemId", "item")
                .put("quantity", 10);
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "removeFromCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, CoreMatchers.startsWith("Connection refused:"));
            async.complete();
        });
    }


    @Test
    public void testCheckoutCart(TestContext context) throws Exception {
        String response = "{\"cartId\" : \"mycart\"}";

        stubFor(post(urlEqualTo("/cart/checkout/mycart"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json").withBody(response)));

        JsonObject msgSent = new JsonObject()
            .put("cartId", "mycart");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "checkoutCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            JsonObject result = ar.result().body();
            assertThat(result.containsKey("cartId"), is(true));
            assertThat(result.getString("cartId"), equalTo("mycart"));
            wireMockRule.verify(postRequestedFor(urlEqualTo("/cart/checkout/mycart")));
            async.complete();
        });
    }

    @Test
    public void testCheckoutCartWhenCartServiceThrowsError(TestContext context) throws Exception {

        stubFor(post(urlEqualTo("/cart/checkout/mycart"))
                .willReturn(
                aResponse().withStatus(500)));

        JsonObject msgSent = new JsonObject()
                .put("cartId", "mycart");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "checkoutCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, equalTo("Cart Service HTTP status code: 500"));
            wireMockRule.verify(postRequestedFor(urlEqualTo("/cart/checkout/mycart")));
            async.complete();
        });
    }

    @Test
    public void testCheckoutCartWhenCartServiceIsDown(TestContext context) throws Exception {

        wireMockRule.shutdownServer();
        Awaitility.await().atMost(Duration.ONE_SECOND).until(() -> !wireMockRule.isRunning());

        JsonObject msgSent = new JsonObject()
                .put("cartId", "mycart");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "checkoutCart");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("CartService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, CoreMatchers.startsWith("Connection refused:"));
            async.complete();
        });
    }
}
