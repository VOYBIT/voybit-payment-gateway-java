package com.voybit.paymentgateway;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CreateCheckoutSessionRequest {
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public CreateCheckoutSessionRequest fiatAmount(String value) {
        return put("fiat_amount", value);
    }

    public CreateCheckoutSessionRequest fiatCurrency(String value) {
        return put("fiat_currency", value);
    }

    public CreateCheckoutSessionRequest description(String value) {
        return put("description", value);
    }

    public CreateCheckoutSessionRequest metadata(Map<String, ?> value) {
        return put("metadata", value);
    }

    public CreateCheckoutSessionRequest paymentWindowSeconds(long value) {
        return put("payment_window_seconds", value);
    }

    Map<String, Object> fields() {
        return fields;
    }

    private CreateCheckoutSessionRequest put(String key, Object value) {
        fields.put(key, value);
        return this;
    }
}
