package com.voybit.paymentgateway;

public final class VoybitException extends RuntimeException {
    private final int status;
    private final String errorCode;
    private final String requestId;
    private final long retryAfterSeconds;

    public VoybitException(int status, String errorCode, String message, String requestId) {
        this(status, errorCode, message, requestId, 0);
    }

    VoybitException(int status, String errorCode, String message, String requestId, long retryAfterSeconds) {
        super(message == null || message.isBlank() ? "payment gateway returned HTTP " + status : message);
        this.status = status;
        this.errorCode = errorCode == null || errorCode.isBlank() ? "unknown_error" : errorCode;
        this.requestId = requestId == null ? "" : requestId;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int status() {
        return status;
    }

    public String errorCode() {
        return errorCode;
    }

    public String requestId() {
        return requestId;
    }

    long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
