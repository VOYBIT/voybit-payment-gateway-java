package com.voybit.paymentgateway;

public record CreatedCheckoutSession(CheckoutSession checkoutSession, boolean replayed, String requestId) {
}
