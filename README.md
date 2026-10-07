# Voybit payment gateway for Java

Create a payment and verify its webhook. Keep the API key and webhook secret on your server. Requires Java 17 or later.

```java
import com.voybit.paymentgateway.Client;
import com.voybit.paymentgateway.Webhook;
```

Maven coordinates: `com.voybit:payment-gateway`. Repository: [github.com/VOYBIT/voybit-payment-gateway-java](https://github.com/VOYBIT/voybit-payment-gateway-java).

## Create a payment

`POST https://api.voybit.com/api/v1/gateway/payments`

| Header | |
| --- | --- |
| `X-Voybit-Api-Key` | Gateway API key. |
| `Idempotency-Key` | 8–128 characters: letters, digits, `.` `_` `:` `-`. Reuse it only with the same body. |

| Field | |
| --- | --- |
| `asset_id` | Required. Asset enabled on the gateway. |
| `crypto_amount` | Required. Decimal string, not a JSON number. |
| `amount_minor` | Required. Fiat amount in minor units. `2500` is 25.00. |
| `fiat_currency` | Required. Three letters, such as `USD`. |
| `gateway_id` | Optional. Omit it when the key is already scoped to one gateway. |
| `expires_in_seconds` | Optional. 300–86400. Default 900. |
| `description` | Optional. Maximum 500 characters. |
| `metadata` | Optional object. Maximum 16 KiB. |

A new payment returns `201`. The same key and body return `200`. A different body returns `409`.

Send the payer to `checkout_url`. Fulfil an order only when `status` is `paid` or `overpaid`.

```java
Client client = new Client(System.getenv("VOYBIT_API_KEY"));
CreatedPayment created = client.createPayment(new CreatePaymentRequest()
        .assetId(System.getenv("VOYBIT_ASSET_ID"))
        .cryptoAmount("25.0000")
        .amountMinor(2500)
        .fiatCurrency("USD")
        .description("Order 1001")
        .metadata(Map.of("order_id", "1001")),
        "order:1001:attempt:1");
```

## Webhook

Read the raw body and verify it before parsing. The signature is `v1=` plus HMAC-SHA256 of `<id>.<timestamp>.<raw body>`, using the gateway webhook secret.

| Header | |
| --- | --- |
| `Voybit-Webhook-Id` | Delivery id. Ignore a repeat. |
| `Voybit-Webhook-Timestamp` | Unix seconds. Reject values outside 5 minutes. |
| `Voybit-Webhook-Signature` | `v1=` and the hex signature. |

`type` is `payment.` plus the status, for example `payment.paid`.

```java
byte[] raw = request.getInputStream().readAllBytes();
Webhook.verify(System.getenv("VOYBIT_WEBHOOK_SECRET"), id, timestamp, signature, raw);
Map<String, Object> event = Webhook.parse(raw);
```
