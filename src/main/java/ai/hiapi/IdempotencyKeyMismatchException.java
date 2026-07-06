package ai.hiapi;

/**
 * {@code error_code=IDEMPOTENCY_KEY_MISMATCH} (422) — this {@code Idempotency-Key}
 * was already used with a different request body.
 *
 * <p>Not retryable: the key construction is buggy (two distinct requests derived
 * the same key).</p>
 */
public class IdempotencyKeyMismatchException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new idempotency-key-mismatch exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public IdempotencyKeyMismatchException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
