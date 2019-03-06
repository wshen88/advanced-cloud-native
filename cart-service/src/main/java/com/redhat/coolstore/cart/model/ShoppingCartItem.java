package com.redhat.coolstore.cart.model;

import java.io.Serializable;

public class ShoppingCartItem implements Serializable {

    private static final long serialVersionUID = 6964558044240061049L;

    private double price;

    private int quantity;

    private Product product;

    public ShoppingCartItem() {
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    @Override
    public String toString() {
        return "ShoppingCartItem [price=" + price + ", quantity=" + quantity + ", product=" + product + "]";
    }
}
