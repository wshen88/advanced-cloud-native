package com.redhat.coolstore.api.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.redhat.coolstore.api.gateway.model.Product;
import com.redhat.coolstore.api.gateway.model.ShoppingCart;
import com.redhat.coolstore.api.gateway.model.ShoppingCartItem;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class CartGatewayTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private CamelContext camelContext;

    @Rule
    public WireMockRule cartServiceMock = new WireMockRule(WireMockConfiguration.wireMockConfig().dynamicPort());

    @Test
    @DirtiesContext
    public void getCart() throws Exception {

        cartServiceMock.stubFor(get(urlEqualTo("/cart/FOO")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(buildShoppingCartResponse())));

        NotifyBuilder notify = new NotifyBuilder(camelContext).fromRoute("getCartRoute").whenDone(1).create();

        adviceCamelContext("getCartRoute");

        ResponseEntity<String> response = restTemplate.getForEntity("/api/cart/FOO", String.class);

        assertThat(notify.matches(10, TimeUnit.SECONDS), is(true));

        assertThat(response.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));

        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(response.getBody());

        assertThat(node.get("shoppingCartItemList").get(0).get("product").get("itemId").asText(), equalTo("p1"));
        assertThat(node.get("cartTotal").asInt(), equalTo(20));
        assertCorsHeaders(response);
        cartServiceMock.verify(getRequestedFor(urlEqualTo("/cart/FOO")));
    }

    @Test
    @DirtiesContext
    public void addToCart() throws Exception {

        cartServiceMock.stubFor(post(urlEqualTo("/cart/FOO/p1/2")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(buildShoppingCartResponse())));

        NotifyBuilder notify = new NotifyBuilder(camelContext).fromRoute("addToCartRoute").whenDone(1).create();

        adviceCamelContext("addToCartRoute");

        ResponseEntity<String> response = restTemplate.postForEntity("/api/cart/FOO/p1/2", null, String.class);

        assertThat(notify.matches(10, TimeUnit.SECONDS), is(true));

        assertThat(response.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));

        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(response.getBody());

        assertThat(node.get("shoppingCartItemList").get(0).get("product").get("itemId").asText(), equalTo("p1"));
        assertThat(node.get("cartTotal").asInt(), equalTo(20));
        assertCorsHeaders(response);
        cartServiceMock.verify(postRequestedFor(urlEqualTo("/cart/FOO/p1/2")));
    }

    @Test
    @DirtiesContext
    public void deleteFromCart() throws Exception {

        cartServiceMock.stubFor(delete(urlEqualTo("/cart/FOO/p1/3")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(buildShoppingCartResponse())));

        NotifyBuilder notify = new NotifyBuilder(camelContext).fromRoute("deleteFromCartRoute").whenDone(1).create();

        adviceCamelContext("deleteFromCartRoute");

        ResponseEntity<String> response = restTemplate.exchange("/api/cart/FOO/p1/3",HttpMethod.DELETE, null, String.class);

        assertThat(notify.matches(10, TimeUnit.SECONDS), is(true));

        assertThat(response.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));

        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(response.getBody());

        assertThat(node.get("shoppingCartItemList").get(0).get("product").get("itemId").asText(), equalTo("p1"));
        assertThat(node.get("cartTotal").asInt(), equalTo(20));
        assertCorsHeaders(response);
        cartServiceMock.verify(deleteRequestedFor(urlEqualTo("/cart/FOO/p1/3")));
    }

    @Test
    @DirtiesContext
    public void checkoutCart() throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        ShoppingCart cart = new ShoppingCart();
        String cartResponseStr = mapper.writeValueAsString(cart);

        cartServiceMock.stubFor(post(urlEqualTo("/cart/checkout/FOO")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "application/json")
                .withBody(cartResponseStr)));

        NotifyBuilder notify = new NotifyBuilder(camelContext).fromRoute("checkoutRoute").whenDone(1).create();

        adviceCamelContext("checkoutRoute");

        ResponseEntity<String> response = restTemplate.postForEntity("/api/cart/checkout/FOO", null, String.class);

        assertThat(notify.matches(10, TimeUnit.SECONDS), is(true));

        assertThat(response.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));

        JsonNode node = new ObjectMapper(new JsonFactory()).readTree(response.getBody());

        assertThat(node.get("shoppingCartItemList").has(0), Matchers.is(false));
        assertThat(node.get("cartTotal").asInt(), equalTo(0));
        assertCorsHeaders(response);
        cartServiceMock.verify(postRequestedFor(urlEqualTo("/cart/checkout/FOO")));
    }

    @Test
    @DirtiesContext
    public void optionsRequest() throws Exception {

        NotifyBuilder notify = new NotifyBuilder(camelContext).whenDone(1).create();

        ResponseEntity<String> response = restTemplate.exchange("/api/cart/FOO/p1/3",HttpMethod.OPTIONS, null, String.class);
        assertThat(response.getStatusCodeValue(), equalTo(HttpStatus.SC_OK));
        assertCorsHeaders(response);
        assertThat(notify.matches(10, TimeUnit.SECONDS), is(true));
    }

    private String buildShoppingCartResponse() throws Exception{
        ObjectMapper mapper = new ObjectMapper();
        ShoppingCart cart = new ShoppingCart();
        Product product = new Product();
        product.setItemId("p1");
        product.setDesc("Test Product Description");
        product.setName("Test Product");
        product.setPrice(10.0);
        product.setAvailability(null);
        ShoppingCartItem item = new ShoppingCartItem();
        item.setProduct(product);
        item.setQuantity(2);
        item.setPromoSavings(5.0);
        item.setPrice(product.getPrice()*item.getQuantity());
        List<ShoppingCartItem> items = new ArrayList<ShoppingCartItem>();
        items.add(item);
        cart.setId("FOO");
        cart.setShoppingCartItemList(items);
        cart.setCartItemTotal(20);
        cart.setCartItemPromoSavings(5.0);
        cart.setShippingTotal(0);
        cart.setShippingPromoSavings(0);
        cart.setCartTotal(20);
        return mapper.writeValueAsString(cart);
    }

    private void adviceCamelContext(String route) throws Exception{
        camelContext.getRouteDefinition(route).adviceWith(camelContext, new AdviceWithRouteBuilder() {

            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("http4://DUMMY")
                    .process(new Processor() {

                        @Override
                        public void process(Exchange exchange) throws Exception {
                            exchange.getIn().setHeader(Exchange.HTTP_URI, "http://localhost:" + cartServiceMock.port());
                        }
                    });
            }
        });
    }

    private void assertCorsHeaders(ResponseEntity<?> response) {
        assertThat(response.getHeaders().get("Access-Control-Allow-Origin"), notNullValue());
        assertThat(response.getHeaders().get("Access-Control-Allow-Origin").get(0), equalTo("*"));
        assertThat(response.getHeaders().get("Access-Control-Allow-Methods"), notNullValue());
        assertThat(response.getHeaders().get("Access-Control-Allow-Methods").get(0), equalTo("GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH"));
    }
}
