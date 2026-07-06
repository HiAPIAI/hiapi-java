package ai.hiapi;

/**
 * {@code error_code=IDEMPOTENCY_KEY_PROCESSING} (409) — the first request with
 * this {@code Idempotency-Key} is still in flight.
 *
 * <p>Retryable: wait {@code Retry-After} seconds and resend the identical
 * request; it will replay the original task once created. The transport already
 * retries this automatically up to {@code maxRetries} before letting it
 * surface.</p>
 */
public class IdempotencyKeyProcessingException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new idempotency-key-processing exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public IdempotencyKeyProcessingException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
