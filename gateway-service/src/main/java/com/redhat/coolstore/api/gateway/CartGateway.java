package com.redhat.coolstore.api.gateway;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.redhat.coolstore.api.gateway.model.ShoppingCart;

@Component
public class CartGateway extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        restConfiguration("cart")
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .enableCORS(true);

        rest("/cart").description("Shopping Cart Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

        .get("/{cartId}").description("Get the current user's shopping cart content")
            .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
            .outType(ShoppingCart.class)
            .route().id("getCartRoute")
                .removeHeaders("CamelHttp*")
                .setBody().simple("null")
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                .setHeader(Exchange.HTTP_PATH, simple("cart/${header.cartId}"))
                .setHeader(Exchange.HTTP_URI, simple("{{cart.service.url}}"))
                .to("http4://DUMMY")
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
        .endRest()

        .post("/{cartId}/{itemId}/{quantity}").description("Add items from current user's shopping cart")
            .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
            .param().name("itemId").type(RestParamType.path).description("The ID of the item to add").dataType("string").endParam()
            .param().name("quantity").type(RestParamType.path).description("The number of items to add").dataType("integer").endParam()
            .outType(ShoppingCart.class)
            .route().id("addToCartRoute")
                .removeHeaders("CamelHttp*")
                .setBody(simple("null"))
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .setHeader(Exchange.HTTP_PATH, simple("cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                .setHeader(Exchange.HTTP_URI, simple("{{cart.service.url}}"))
                .to("http4://DUMMY")
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
        .endRest()

        .delete("/{cartId}/{itemId}/{quantity}").description("Delete items from current user's shopping cart")
            .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
            .param().name("itemId").type(RestParamType.path).description("The ID of the item to delete").dataType("string").endParam()
            .param().name("quantity").type(RestParamType.path).description("The number of items to delete").dataType("integer").endParam()
            .outType(ShoppingCart.class)
            .route().id("deleteFromCartRoute")
                .removeHeaders("CamelHttp*")
                .setBody(simple("null"))
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.DELETE)
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .setHeader(Exchange.HTTP_PATH, simple("cart/${header.cartId}/${header.itemId}/${header.quantity}"))
                .setHeader(Exchange.HTTP_URI, simple("{{cart.service.url}}"))
                .to("http4://DUMMY")
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
        .endRest()

        .post("/checkout/{cartId}").description("Finalize shopping cart and process payment")
            .param().name("cartId").type(RestParamType.path).description("The ID of the cart to process").dataType("string").endParam()
            .outType(ShoppingCart.class)
            .route().id("checkoutRoute")
                .removeHeaders("CamelHttp*")
                .setBody(simple("null"))
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.POST)
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .setHeader(Exchange.HTTP_PATH, simple("cart/checkout/${header.cartId}"))
                .setHeader(Exchange.HTTP_URI, simple("{{cart.service.url}}"))
                .to("http4://DUMMY")
                .setHeader("CamelJacksonUnmarshalType", simple(ShoppingCart.class.getName()))
                .unmarshal().json(JsonLibrary.Jackson, ShoppingCart.class)
        .endRest();
    }
}
