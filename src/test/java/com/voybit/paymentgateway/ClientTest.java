package com.voybit.paymentgateway;

import com.sun.net.httpserver.HttpServer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientTest {
    public static void main(String[] args) throws Exception {
        testCreatePayment();
        testValidationIsNotRetried();
        testWebhook();
        testJsonRoundTrip();
        System.out.println("java ok");
    }

    private static void testCreatePayment() throws Exception {
        AtomicReference<byte[]> seen = new AtomicReference<>();
        AtomicReference<String> apiKey = new AtomicReference<>();
        AtomicReference<String> idempotency = new AtomicReference<>();
        HttpServer server = server(201, """
                {"id":"pay_1","status":"pending","checkout_url":"https://voybit.com/pay/pub_1","amount_minor":2500,"deposit_instructions":{"address":"TExample"}}
                """, seen, apiKey, idempotency, new AtomicInteger());
        try {
            Client client = new Client("vb_test_example_secret", base(server));
            CreatedPayment created = client.createPayment(new CreatePaymentRequest()
                    .assetId("asset")
                    .cryptoAmount("25.0000")
                    .amountMinor(2500)
                    .fiatCurrency("USD"), "order:1001:attempt:1");
            check("https://voybit.com/pay/pub_1".equals(created.payment().checkoutUrl()), "checkout");
            check("TExample".equals(created.payment().deposit().get("address")), "deposit");
            check("vb_test_example_secret".equals(apiKey.get()), "api key");
            check("order:1001:attempt:1".equals(idempotency.get()), "idempotency");
            check(Long.valueOf(2500).equals(Json.object(new String(seen.get(), StandardCharsets.UTF_8)).get("amount_minor")), "amount");
        } finally {
            server.stop(0);
        }
    }

    private static void testValidationIsNotRetried() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = server(422, """
                {"error":{"code":"asset_unavailable","message":"That asset is not enabled for this gateway."}}
                """, new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>(), calls);
        try {
            Client client = new Client("vb_test_example_secret", base(server));
            try {
                client.createPayment(new CreatePaymentRequest().assetId("asset").cryptoAmount("1").amountMinor(100).fiatCurrency("USD"), "order:1001:attempt:1");
                throw new AssertionError("validation error was not raised");
            } catch (VoybitException error) {
                check("asset_unavailable".equals(error.errorCode()), "code");
                check(calls.get() == 1, "calls");
            }
        } finally {
            server.stop(0);
        }
    }

    private static void testWebhook() throws Exception {
        byte[] raw = "{\"type\":\"payment.paid\",\"status\":\"paid\"}".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        String timestamp = "1700000000";
        String signature = "v1=" + sign("whsec_example", "delivery-1." + timestamp + ".", raw);
        Webhook.verify("whsec_example", "delivery-1", timestamp, signature, raw, now);
        check("paid".equals(Webhook.parse(raw).get("status")), "status");
        try {
            byte[] tampered = "{\"type\":\"payment.paid\",\"status\":\"paid\"} ".getBytes(StandardCharsets.UTF_8);
            Webhook.verify("whsec_example", "delivery-1", timestamp, signature, tampered, now);
            throw new AssertionError("tampered body was accepted");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("does not match"), "tamper message");
        }
    }

    private static void testJsonRoundTrip() {
        String json = Json.stringify(Map.of("note", "line\nnext", "amount_minor", 2500L, "ok", true));
        Map<String, Object> parsed = Json.object(json);
        check("line\nnext".equals(parsed.get("note")), "string");
        check(Long.valueOf(2500).equals(parsed.get("amount_minor")), "number");
        check(Boolean.TRUE.equals(parsed.get("ok")), "bool");
    }

    private static HttpServer server(int status, String body, AtomicReference<byte[]> seen, AtomicReference<String> apiKey, AtomicReference<String> idempotency, AtomicInteger calls) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/gateway/payments", exchange -> {
            calls.incrementAndGet();
            seen.set(exchange.getRequestBody().readAllBytes());
            apiKey.set(exchange.getRequestHeaders().getFirst("X-Voybit-Api-Key"));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, payload.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(payload);
            }
        });
        server.start();
        return server;
    }

    private static String base(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    private static String sign(String secret, String prefix, byte[] raw) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update(prefix.getBytes(StandardCharsets.UTF_8));
        mac.update(raw);
        return HexFormat.of().formatHex(mac.doFinal());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
