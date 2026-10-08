# Voybit payment gateway for Java

## Get an API key

1. Create an account at [dashboard.voybit.com](https://dashboard.voybit.com).
2. Open **Gateways**, create a payment gateway, enable the assets customers may choose, and store its webhook secret as `VOYBIT_WEBHOOK_SECRET`.
3. Open **API keys**, choose **Create secret key**, and bind it to that gateway. Copy the full `vb_live_…` value once and store it as `VOYBIT_API_KEY` on your server.

Create a payment and verify its webhook. Keep the API key and webhook secret on your server. Requires Java 17 or later.

```java
import com.voybit.paymentgateway.Client;
import com.voybit.paymentgateway.Webhook;
```

Maven coordinates: `com.voybit:payment-gateway`. Repository: [github.com/VOYBIT/voybit-payment-gateway-java](https://github.com/VOYBIT/voybit-payment-gateway-java).

## Create a buyer-choice checkout

`POST https://api.voybit.com/api/v1/gateway/checkout-sessions`

| Header | |
| --- | --- |
| `X-Voybit-Api-Key` | Gateway API key. |
| `Idempotency-Key` | 8–128 characters: letters, digits, `.` `_` `:` `-`. Reuse it only with the same body. |

| Field | |
| --- | --- |
| `fiat_amount` | Required positive decimal string, such as `25.00`. |
| `fiat_currency` | Required: `USD`, `EUR`, or `GBP`. |
| `payment_window_seconds` | Optional. 300–86400. Default 900. |
| `description` | Optional. Maximum 500 characters. |
| `metadata` | Optional object. Maximum 16 KiB. |

A new payment returns `201`. The same key and body return `200`. A different body returns `409`.

Send the payer to `checkout_url`. The payer chooses from the gateway’s enabled assets and confirms a live quote before the address and QR are created. Fulfil an order only when `status` is `paid` or `overpaid`.

```java
Client client = new Client(System.getenv("VOYBIT_API_KEY"));
CreatedCheckoutSession created = client.createCheckoutSession(new CreateCheckoutSessionRequest()
        .fiatAmount("25.00")
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
