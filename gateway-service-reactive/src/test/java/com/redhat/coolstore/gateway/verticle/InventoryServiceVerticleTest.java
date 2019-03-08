package com.redhat.coolstore.gateway.verticle;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.awaitility.Awaitility;
import org.awaitility.Duration;
import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class InventoryServiceVerticleTest {

    private Vertx vertx;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    @Before
    public void setUp(TestContext context) throws IOException {
      vertx = Vertx.vertx();

      // Register the context exception handler
      vertx.exceptionHandler(context.exceptionHandler());

      JsonObject config = new JsonObject()
          .put("inventory.service.host", "localhost")
          .put("inventory.service.port", wireMockRule.port());
      DeploymentOptions options = new DeploymentOptions().setConfig(config);

      // We pass the options as the second parameter of the deployVerticle method.
      vertx.deployVerticle(new InventoryServiceVerticle(), options, context.asyncAssertSuccess());
    }

    @Test
    public void testGetInventory(TestContext context) throws Exception {

        stubFor(get(urlPathEqualTo("/inventory/p1")).withQueryParam("storeStatus", equalTo("true"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json")
                    .withBody(new JsonObject().put("itemId", "p1").encode())));

        JsonObject msgSent = new JsonObject()
            .put("itemId", "p1");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getInventory");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("InventoryService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            assertThat(ar.result().body().getString("itemId"), CoreMatchers.equalTo("p1"));
            wireMockRule.verify(getRequestedFor(urlPathEqualTo("/inventory/p1")));
            async.complete();
        });
    }

    @Test
    public void testGetInventoryNotFound(TestContext context) throws Exception {

        stubFor(get(urlPathEqualTo("/inventory/p1")).withQueryParam("storeStatus", equalTo("true"))
                .willReturn(
                aResponse().withStatus(404)));

        JsonObject msgSent = new JsonObject()
            .put("itemId", "p1");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getInventory");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("InventoryService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), nullValue());
            wireMockRule.verify(getRequestedFor(urlPathEqualTo("/inventory/p1")));
            async.complete();
        });
    }

    @Test
    public void testGetInventoryWhenInventoryServiceThrowsError(TestContext context) throws Exception {

        stubFor(get(urlPathEqualTo("/inventory/p1")).withQueryParam("storeStatus", equalTo("true"))
                .willReturn(
                aResponse().withStatus(500)));

        JsonObject msgSent = new JsonObject()
            .put("itemId", "p1");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getInventory");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("InventoryService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ar.cause().getMessage();
            assertThat(errorMessage, CoreMatchers.equalTo("Inventory Service HTTP status code: 500"));
            wireMockRule.verify(getRequestedFor(urlPathEqualTo("/inventory/p1")));
            async.complete();
        });
    }

    @Test
    public void testGetInventoryWhenInventoryServiceIsDown(TestContext context) throws Exception {

        wireMockRule.shutdownServer();
        Awaitility.await().atMost(Duration.ONE_SECOND).until(() -> !wireMockRule.isRunning());

        JsonObject msgSent = new JsonObject()
            .put("itemId", "p1");
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getInventory");
        Async async = context.async();
        vertx.eventBus().<JsonObject>send("InventoryService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));
            assertThat(ar.cause() instanceof ReplyException, is(true));
            String errorMessage = ((ReplyException)ar.cause()).getMessage();
            assertThat(errorMessage, CoreMatchers.startsWith("Connection refused:"));
            async.complete();
        });
    }
}
