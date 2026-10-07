package com.voybit.paymentgateway;

import java.util.Map;

public final class Payment {
    private final Map<String, Object> fields;

    Payment(Map<String, Object> fields) {
        this.fields = fields;
    }

    public String id() {
        return text("id");
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

    public String cryptoAsset() {
        return text("crypto_asset");
    }

    public String cryptoNetwork() {
        return text("crypto_network");
    }

    public long amountMinor() {
        Object value = fields.get("amount_minor");
        return value instanceof Number number ? number.longValue() : 0L;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> deposit() {
        Object value = fields.get("deposit_instructions");
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String text(String key) {
        Object value = fields.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
