package ai.hiapi;

/**
 * 503 — the platform is temporarily unavailable; retry with backoff.
 */
public class ServiceUnavailableException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new service-unavailable exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public ServiceUnavailableException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
