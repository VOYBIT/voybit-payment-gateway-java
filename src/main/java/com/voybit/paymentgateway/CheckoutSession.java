package com.voybit.paymentgateway;

import java.util.Map;

public final class CheckoutSession {
    private final Map<String, Object> fields;

    CheckoutSession(Map<String, Object> fields) {
        this.fields = fields;
    }

    public String id() {
        return text("id");
    }

    public String sessionId() {
        return text("session_id");
    }

    public String publicId() {
        return text("public_id");
    }

    public String status() {
        return text("status");
    }

    public String checkoutUrl() {
        return text("checkout_url");
    }

    public String fiatAmount() {
        return text("fiat_amount");
    }

    public String fiatCurrency() {
        return text("fiat_currency");
    }

    private String text(String key) {
        Object value = fields.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
