package com.voybit.paymentgateway;

public record CreatedPayment(Payment payment, boolean replayed, String requestId) {
}
