package com.redhat.coolstore.api.gateway;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.component.jackson.ListJacksonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.redhat.coolstore.api.gateway.model.Inventory;
import com.redhat.coolstore.api.gateway.model.Product;

@Component
public class ProductGateway extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        JacksonDataFormat productFormatter = new ListJacksonDataFormat();
        productFormatter.setUnmarshalType(Product.class);

        restConfiguration("product")
            .component("servlet")
            .bindingMode(RestBindingMode.json)
            .enableCORS(true);

        rest("/products/").description("Product Catalog Service")
            .produces(MediaType.APPLICATION_JSON_VALUE)

        .get("/").description("Get Product Catalog").outType(Product.class)
            .route().id("productRoute")
                .setBody(simple("null"))
                .removeHeaders("CamelHttp*")
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
                .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
                .setHeader(Exchange.HTTP_PATH, simple("products"))
                .setHeader(Exchange.HTTP_URI, simple("{{catalog.service.url}}"))
                .to("http4://DUMMY")
                .unmarshal(productFormatter)
            .split(body()).parallelProcessing()
                .enrich("direct:inventory", new InventoryEnricher())
            .end()
          .endRest();

        from("direct:inventory")
            .id("inventoryRoute")
            .setHeader("itemId", simple("${body.itemId}"))
            .setBody(simple("null"))
            .removeHeaders("CamelHttp*")
            .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON_VALUE))
            .setHeader(Exchange.HTTP_METHOD, HttpMethods.GET)
            .setHeader(Exchange.HTTP_PATH, simple("inventory/${header.itemId}"))
            .setHeader(Exchange.HTTP_QUERY, simple("storeStatus=true"))
            .setHeader(Exchange.HTTP_URI, simple("{{inventory.service.url}}"))
            .to("http4://DUMMY1")
            .setHeader("CamelJacksonUnmarshalType", simple(Inventory.class.getName()))
            .unmarshal().json(JsonLibrary.Jackson, Inventory.class);

    }

    private class InventoryEnricher implements AggregationStrategy {
        @Override
        public Exchange aggregate(Exchange original, Exchange resource) {

            // Add the discovered availability to the product and set it back
            Product p = original.getIn().getBody(Product.class);
            Inventory i = resource.getIn().getBody(Inventory.class);
            p.setAvailability(i);
            original.getOut().setBody(p);
            return original;
        }
    }

}
