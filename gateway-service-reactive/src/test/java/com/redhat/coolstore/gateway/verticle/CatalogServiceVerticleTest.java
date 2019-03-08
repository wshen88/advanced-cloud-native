package com.redhat.coolstore.gateway.verticle;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class CatalogServiceVerticleTest {

    private Vertx vertx;

    @Rule
    public WireMockRule catalogServiceMock = new WireMockRule(wireMockConfig().dynamicPort());

    @Before
    public void setUp(TestContext context) throws IOException {
      vertx = Vertx.vertx();

      // Register the context exception handler
      vertx.exceptionHandler(context.exceptionHandler());

      JsonObject config = new JsonObject()
          .put("catalog.service.host","localhost")
          .put("catalog.service.port", catalogServiceMock.port());
      DeploymentOptions options = new DeploymentOptions().setConfig(config);

      // We pass the options as the second parameter of the deployVerticle method.
      vertx.deployVerticle(new CatalogServiceVerticle(), options, context.asyncAssertSuccess());
    }

    @Test
    public void testGetProducts(TestContext context) throws Exception {

        String catalogServiceResponse = new StringBuilder()
                .append("[")
                .append("{").append("\"itemId\": \"p1\"")
                .append("},")
                .append("{").append("\"itemId\": \"p2\"")
                .append("}").append("]")
                .toString();

        catalogServiceMock.stubFor(get(urlEqualTo("/products"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json").withBody(catalogServiceResponse)));

        String inventoryServiceResponseProduct1 = new StringBuilder()
                .append("{").append("\"itemId\": \"p1\",")
                .append("\"location\": \"somelocation\"")
                .append("}")
                .toString();

        String inventoryServiceResponseProduct2 = new StringBuilder()
                .append("{").append("\"itemId\": \"p2\",")
                .append("\"location\": \"anotherlocation\"")
                .append("}")
                .toString();

        vertx.eventBus().<JsonObject>consumer("InventoryService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("getInventory"));
            assertThat(msg.body().containsKey("itemId"), is(true));
            assertThat(msg.body().getString("itemId"), anyOf(equalTo("p1"), equalTo("p2")));
            if (msg.body().getString("itemId").equals("p1")) {
                msg.reply(new JsonObject(inventoryServiceResponseProduct1));
            } else {
                msg.reply(new JsonObject(inventoryServiceResponseProduct2));
            }
        });

        JsonObject msgSent = new JsonObject();
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getProducts");
        Async async = context.async();
        vertx.eventBus().<JsonArray>send("CatalogService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            JsonArray result = ar.result().body();
            assertThat(result.size(), equalTo(2));
            JsonObject node0 = result.getJsonObject(0);
            assertThat(node0, notNullValue());
            assertThat(node0.getString("itemId"), equalTo("p1"));
            assertThat(node0.getJsonObject("availability"), notNullValue());
            assertThat(node0.getJsonObject("availability").getString("itemId"), equalTo("p1"));
            assertThat(node0.getJsonObject("availability").getString("location"), equalTo("somelocation"));
            JsonObject node1 = result.getJsonObject(1);
            assertThat(node1, notNullValue());
            assertThat(node1.getString("itemId"), equalTo("p2"));
            assertThat(node1.getJsonObject("availability"), notNullValue());
            assertThat(node1.getJsonObject("availability").getString("itemId"), equalTo("p2"));
            assertThat(node1.getJsonObject("availability").getString("location"), equalTo("anotherlocation"));

            catalogServiceMock.verify(getRequestedFor(urlEqualTo("/products")));
            async.complete();
        });
    }

    @Test
    public void testGetProductsWhenInventoryNotFound(TestContext context) throws Exception {
        String catalogServiceResponse = new StringBuilder()
                .append("[")
                .append("{").append("\"itemId\": \"p1\"")
                .append("},")
                .append("{").append("\"itemId\": \"p2\"")
                .append("}").append("]")
                .toString();

        catalogServiceMock.stubFor(get(urlEqualTo("/products"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json").withBody(catalogServiceResponse)));

        String inventoryServiceResponseProduct1 = new StringBuilder()
                .append("{").append("\"itemId\": \"p1\",")
                .append("\"location\": \"somelocation\"")
                .append("}")
                .toString();

        vertx.eventBus().<JsonObject>consumer("InventoryService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("getInventory"));
            assertThat(msg.body().containsKey("itemId"), is(true));
            assertThat(msg.body().getString("itemId"), anyOf(equalTo("p1"), equalTo("p2")));
            if (msg.body().getString("itemId").equals("p1")) {
                msg.reply(new JsonObject(inventoryServiceResponseProduct1));
            } else {
                msg.reply(null);
            }
        });

        JsonObject msgSent = new JsonObject();
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getProducts");
        Async async = context.async();
        vertx.eventBus().<JsonArray>send("CatalogService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            JsonArray result = ar.result().body();
            assertThat(result.size(), equalTo(2));
            JsonObject node0 = result.getJsonObject(0);
            assertThat(node0, notNullValue());
            assertThat(node0.getString("itemId"), equalTo("p1"));
            assertThat(node0.getJsonObject("availability"), notNullValue());
            assertThat(node0.getJsonObject("availability").getString("itemId"), equalTo("p1"));
            assertThat(node0.getJsonObject("availability").getString("location"), equalTo("somelocation"));
            JsonObject node1 = result.getJsonObject(1);
            assertThat(node1, notNullValue());
            assertThat(node1.getString("itemId"), equalTo("p2"));
            assertThat(node1.getJsonObject("availability"), nullValue());

            catalogServiceMock.verify(getRequestedFor(urlEqualTo("/products")));
            async.complete();
        });
    }

    @Test
    public void testGetProductsWhenInventoryServiceThrowsError(TestContext context) throws Exception {
        String catalogServiceResponse = new StringBuilder()
                .append("[")
                .append("{").append("\"itemId\": \"p1\"")
                .append("},")
                .append("{").append("\"itemId\": \"p2\"")
                .append("}").append("]")
                .toString();

        catalogServiceMock.stubFor(get(urlEqualTo("/products"))
                .willReturn(
                aResponse().withStatus(200).withHeader("Content-type", "application/json").withBody(catalogServiceResponse)));

        String inventoryServiceResponseProduct1 = new StringBuilder()
                .append("{").append("\"itemId\": \"p1\",")
                .append("\"location\": \"somelocation\"")
                .append("}")
                .toString();

        vertx.eventBus().<JsonObject>consumer("InventoryService", msg -> {
            assertThat(msg.headers().get("action"), equalTo("getInventory"));
            assertThat(msg.body().containsKey("itemId"), is(true));
            assertThat(msg.body().getString("itemId"), anyOf(equalTo("p1"), equalTo("p2")));
            if (msg.body().getString("itemId").equals("p1")) {
                msg.reply(new JsonObject(inventoryServiceResponseProduct1));
            } else {
                msg.fail(-1, "Inventory Service HTTP status code: 500");
            }
        });

        JsonObject msgSent = new JsonObject();
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getProducts");
        Async async = context.async();
        vertx.eventBus().<JsonArray>send("CatalogService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(false));
            assertThat(ar.result(), notNullValue());
            assertThat(ar.result().body(), notNullValue());
            JsonArray result = ar.result().body();
            assertThat(result.size(), equalTo(2));
            JsonObject node0 = result.getJsonObject(0);
            assertThat(node0, notNullValue());
            assertThat(node0.getString("itemId"), equalTo("p1"));
            assertThat(node0.getJsonObject("availability"), notNullValue());
            assertThat(node0.getJsonObject("availability").getString("itemId"), equalTo("p1"));
            assertThat(node0.getJsonObject("availability").getString("location"), equalTo("somelocation"));
            JsonObject node1 = result.getJsonObject(1);
            assertThat(node1, notNullValue());
            assertThat(node1.getString("itemId"), equalTo("p2"));
            assertThat(node1.containsKey("availability"), is(false));

            catalogServiceMock.verify(getRequestedFor(urlEqualTo("/products")));
            async.complete();
        });
    }

    @Test
    public void testGetProductsWhenCatalogServiceThrowsError(TestContext context) throws Exception {
        catalogServiceMock.stubFor(get(urlEqualTo("/products"))
                .willReturn(
                aResponse().withStatus(500)));


        vertx.eventBus().<JsonObject>consumer("InventoryService", msg -> {
            assertThat("No message expected", false);
        });

        JsonObject msgSent = new JsonObject();
        DeliveryOptions options = new DeliveryOptions();
        options.addHeader("action", "getProducts");
        Async async = context.async();
        vertx.eventBus().<JsonArray>send("CatalogService", msgSent, options, ar -> {
            assertThat(ar.failed(), is(true));

            catalogServiceMock.verify(getRequestedFor(urlEqualTo("/products")));
            async.complete();
        });
    }
}
