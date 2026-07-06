package ai.hiapi;

/**
 * 404 — the task does not exist or does not belong to this account.
 */
public class NotFoundException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new not-found exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public NotFoundException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
