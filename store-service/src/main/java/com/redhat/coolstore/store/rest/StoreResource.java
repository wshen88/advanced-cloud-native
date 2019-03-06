package com.redhat.coolstore.store.rest;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import com.redhat.coolstore.store.model.Store;

@ApplicationScoped
@Path("/store")
public class StoreResource {

    private Map<String, String> stores = new HashMap<>();

    @PostConstruct
    public void initStores() {
        stores = new HashMap<>();
        stores.put("Raleigh", "CLOSED");
        stores.put("Tokyo", "OPEN");
    }

    @GET
    @Path("/status/{location}")
    @Produces(MediaType.APPLICATION_JSON)
    public Store getStatus(@PathParam("location") String location) {
        String status = stores.get(location);
        if (status == null) {
            status = "N/A";
        }
        Store store = new Store();
        store.setLocation(location);
        store.setStatus(status);
        return store;
    }

}
