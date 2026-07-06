package ai.hiapi;

/**
 * Raised when a callback could not be verified (bad signature or stale
 * timestamp).
 */
public class WebhookVerificationException extends HiAPIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new webhook-verification exception.
     *
     * @param message human-readable description of why verification failed
     */
    public WebhookVerificationException(String message) {
        super(message);
    }

    /**
     * Creates a new webhook-verification exception wrapping an underlying cause.
     *
     * @param message human-readable description of why verification failed
     * @param cause   the underlying error (e.g. malformed timestamp or body)
     */
    public WebhookVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
