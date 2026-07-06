package ai.hiapi;

/**
 * {@code error_code=INVALID_REQUEST} — fix the request; do not retry as-is.
 */
public class InvalidRequestException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new invalid-request exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public InvalidRequestException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
