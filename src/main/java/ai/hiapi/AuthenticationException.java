package ai.hiapi;

/**
 * 401 — the API key is missing or invalid.
 */
public class AuthenticationException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new authentication exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public AuthenticationException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
