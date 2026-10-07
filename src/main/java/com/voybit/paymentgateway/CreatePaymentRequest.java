package com.voybit.paymentgateway;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CreatePaymentRequest {
    private final Map<String, Object> fields = new LinkedHashMap<>();

    public CreatePaymentRequest assetId(String value) {
        return put("asset_id", value);
    }

    public CreatePaymentRequest cryptoAmount(String value) {
        return put("crypto_amount", value);
    }

    public CreatePaymentRequest amountMinor(long value) {
        return put("amount_minor", value);
    }

    public CreatePaymentRequest fiatCurrency(String value) {
        return put("fiat_currency", value);
    }

    public CreatePaymentRequest gatewayId(String value) {
        return put("gateway_id", value);
    }

    public CreatePaymentRequest expiresInSeconds(long value) {
        return put("expires_in_seconds", value);
    }

    public CreatePaymentRequest description(String value) {
        return put("description", value);
    }

    public CreatePaymentRequest metadata(Map<String, ?> value) {
        return put("metadata", value);
    }

    Map<String, Object> fields() {
        return fields;
    }

    private CreatePaymentRequest put(String key, Object value) {
        fields.put(key, value);
        return this;
    }
}
