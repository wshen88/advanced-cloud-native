package com.redhat.coolstore.api.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.redhat.coolstore.api.gateway.model.Inventory;
import com.redhat.coolstore.api.gateway.model.Product;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ProductGatewayTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CamelContext camelContext;

    @Rule
    public WireMockRule catalogServiceMock = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

    @Rule
    public WireMockRule inventoryServiceMock = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

    @Test
    @DirtiesContext
    public void getProductCatalogEnrichedWithInventory() throws Exception {

        List<Product> products = new ArrayList<Product>();
        Product p1 = new Product();
        p1.setItemId("p1");
        p1.setDesc("description");
        p1.setName("product1");
        p1.setPrice(10.0);
        products.add(p1);

        Product p2 = new Product();
        p2.setItemId("p2");
        p2.setDesc("description");
        p2.setName("product2");
        p2.setPrice(10.0);
        products.add(p2);

        Inventory inventory1 = new Inventory();
        inventory1.setItemId("p1");
        inventory1.setQuantity(1);
        inventory1.setLocation("Local Store");
        inventory1.setLink("http://developers.redhat/com");

        Inventory inventory2 = new Inventory();
        inventory2.setItemId("p2");
        inventory2.setQuantity(2);
        inventory2.setLocation("Local Store");
        inventory2.setLink("http://developers.redhat/com");

        ObjectMapper mapper = new ObjectMapper();
        String productResponseStr = mapper.writeValueAsString(products);
        String inventory1ResponseStr = mapper.writeValueAsString(inventory1);
        String inventory2ResponseStr = mapper.writeValueAsString(inventory2);

        catalogServiceMock.stubFor(get(urlMatching("/products")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(productResponseStr)));

        inventoryServiceMock.stubFor(get(urlPathEqualTo("/inventory/p1"))
                .withQueryParam("storeStatus", equalTo("true"))
                .willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(inventory1ResponseStr)));

        inventoryServiceMock.stubFor(get(urlPathEqualTo("/inventory/p2"))
                .withQueryParam("storeStatus", equalTo("true"))
                .willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(inventory2ResponseStr)));

        NotifyBuilder notify = new NotifyBuilder(camelContext).fromRoute("productRoute").whenDone(1).create();

        adviceCamelContext();

        ResponseEntity<String> response = restTemplate.getForEntity("/api/products", String.class);
        assertThat(response.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));

        assertThat(notify.matches(10, TimeUnit.SECONDS), is(true));

        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(response.getBody());
        assertThat(node.get(0).get("itemId").asText(), Matchers.equalTo("p1"));
        assertThat(node.get(0).get("availability").get("itemId").asText(), Matchers.equalTo("p1"));
        assertThat(node.get(0).get("availability").get("quantity").asInt(), equalTo(1));
        assertThat(node.get(1).get("itemId").asText(), Matchers.equalTo("p2"));
        assertThat(node.get(1).get("availability").get("itemId").asText(), Matchers.equalTo("p2"));
        assertThat(node.get(1).get("availability").get("quantity").asInt(), equalTo(2));
        assertCorsHeaders(response);
        catalogServiceMock.verify(getRequestedFor(urlEqualTo("/products")));
        inventoryServiceMock.verify(2, getRequestedFor(urlMatching("/inventory/.*")));
    }

    private void adviceCamelContext() throws Exception {
        camelContext.getRouteDefinition("productRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {

            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("http4://DUMMY")
                    .process(new Processor() {

                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(Exchange.HTTP_URI, "http://localhost:" + catalogServiceMock.port());
                        }
                    });
            }
        });

        camelContext.getRouteDefinition("inventoryRoute").adviceWith(camelContext, new AdviceWithRouteBuilder() {

            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("http4://DUMMY1")
                    .process(new Processor() {

                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(Exchange.HTTP_URI, "http://localhost:" + inventoryServiceMock.port());
                        }
                    });
            }
        });
    }

    private void assertCorsHeaders(ResponseEntity<?> response) {
        assertThat(response.getHeaders().get("Access-Control-Allow-Origin"), notNullValue());
        assertThat(response.getHeaders().get("Access-Control-Allow-Origin").get(0), Matchers.equalTo("*"));
        assertThat(response.getHeaders().get("Access-Control-Allow-Methods"), notNullValue());
        assertThat(response.getHeaders().get("Access-Control-Allow-Methods").get(0), Matchers.equalTo("GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH"));
    }

}
