package com.redhat.coolstore.cart.service;

import org.springframework.stereotype.Component;

import com.redhat.coolstore.cart.model.ShoppingCart;

@Component
public class PriceCalculationServiceImpl implements PriceCalculationService {

    @Override
    public void priceShoppingCart(ShoppingCart sc) {
        // calculate the cartItemTotal
        sc.setCartItemTotal(sc.getShoppingCartItemList().stream().mapToDouble(sci -> sci.getPrice() * sci.getQuantity()).sum());
        sc.setShippingTotal(calculateShipping(sc));
        sc.setCartTotal(sc.getCartItemTotal() + sc.getShippingTotal());
    }

    private double calculateShipping(ShoppingCart sc) {
        if (sc.getCartItemTotal() <= 0.0) {
            return 0.0;
        } else if (sc.getCartItemTotal() > 0.0 && sc.getCartItemTotal() < 25) {
            return 2.99;
        } else if (sc.getCartItemTotal() >= 25.0 && sc.getCartItemTotal() < 49.99) {
            return 4.99;
        } else if (sc.getCartItemTotal() >= 50.0 && sc.getCartItemTotal() < 74.99) {
            return 6.99;
        } else {
            return 0;
        }
    }

}
