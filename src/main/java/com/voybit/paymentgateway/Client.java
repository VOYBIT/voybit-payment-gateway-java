package com.voybit.paymentgateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Client {
    public static final String DEFAULT_BASE_URL = "https://api.voybit.com/api/v1";
    private static final String USER_AGENT = "voybit-payment-gateway-java/0.1.0";
    private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");
    private static final Set<Integer> RETRYABLE = Set.of(408, 429, 500, 502, 503, 504);
    private static final int MAX_BODY = 1 << 20;

    private final String apiKey;
    private final String endpoint;
    private final HttpClient http;

    public Client(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL);
    }

    public Client(String apiKey, String baseUrl) {
        this(apiKey, baseUrl, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(20))
                .build());
    }

    Client(String apiKey, String baseUrl, HttpClient http) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API key is required");
        }
        this.apiKey = apiKey;
        String root = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.replaceAll("/+$", "");
        this.endpoint = root + "/gateway/payments";
        this.http = http;
    }

    public CreatedPayment createPayment(CreatePaymentRequest request, String idempotencyKey) throws IOException, InterruptedException {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (idempotencyKey == null || !IDEMPOTENCY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key must be 8 to 128 URL-safe characters");
        }
        byte[] body = Json.stringify(request.fields()).getBytes(StandardCharsets.UTF_8);
        VoybitException lastApi = null;
        IOException lastIo = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                return post(body, idempotencyKey);
            } catch (VoybitException error) {
                if (!RETRYABLE.contains(error.status()) || attempt == 3) {
                    throw error;
                }
                lastApi = error;
                Thread.sleep(delayMillis(attempt, error.retryAfterSeconds()));
            } catch (IOException error) {
                if (attempt == 3) {
                    throw error;
                }
                lastIo = error;
                Thread.sleep(delayMillis(attempt, 0));
            }
        }
        if (lastApi != null) {
            throw lastApi;
        }
        if (lastIo != null) {
            throw lastIo;
        }
        throw new IOException("payment gateway request failed");
    }

    private CreatedPayment post(byte[] body, String idempotencyKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(20))
                .header("X-Voybit-Api-Key", apiKey)
                .header("Idempotency-Key", idempotencyKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        byte[] raw = response.body() == null ? new byte[0] : response.body();
        String requestId = response.headers().firstValue("x-request-id").orElse("");
        if (raw.length > MAX_BODY) {
            throw new VoybitException(response.statusCode(), "body_too_large", "response was too large", requestId);
        }
        int status = response.statusCode();
        Map<String, Object> decoded = decode(raw, status, requestId);
        if (status >= 200 && status < 300) {
            boolean replayed = "true".equals(response.headers().firstValue("idempotency-replayed").orElse(""));
            return new CreatedPayment(new Payment(decoded), replayed, requestId);
        }
        Object errorValue = decoded.get("error");
        String code = "unknown_error";
        String message = "";
        if (errorValue instanceof Map<?, ?> error) {
            if (error.get("code") != null) {
                code = String.valueOf(error.get("code"));
            }
            if (error.get("message") != null) {
                message = String.valueOf(error.get("message"));
            }
        }
        long retryAfter = 0;
        String retryHeader = response.headers().firstValue("retry-after").orElse("");
        if (!retryHeader.isBlank()) {
            try {
                retryAfter = Long.parseLong(retryHeader.trim());
            } catch (NumberFormatException ignored) {
                retryAfter = 0;
            }
        }
        throw new VoybitException(status, code, message, requestId, retryAfter);
    }

    private static Map<String, Object> decode(byte[] raw, int status, String requestId) {
        if (raw.length == 0) {
            return Map.of();
        }
        try {
            return Json.object(new String(raw, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            if (status >= 200 && status < 300) {
                throw new VoybitException(status, "invalid_response", "response was not JSON", requestId);
            }
            return Map.of();
        }
    }

    private static long delayMillis(int attempt, long retryAfterSeconds) {
        if (retryAfterSeconds > 0) {
            return Math.min(retryAfterSeconds, 30) * 1000L;
        }
        return Math.min(500L << attempt, 8000L);
    }
}
