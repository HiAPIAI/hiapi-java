package ai.hiapi;

/**
 * Raised when the server returns a non-2xx HTTP response.
 *
 * <p>Carries the HTTP {@code status}, the business {@code errorCode} from the
 * error envelope (may be {@code null}), and the raw decoded response {@code body}
 * (may be {@code null}).</p>
 */
public class APIException extends HiAPIException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String errorCode;
    private final String body;

    /**
     * Creates a new API exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public APIException(String message, int status, String errorCode, String body) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
        this.body = body;
    }

    /**
     * Returns the HTTP status code of the failing response.
     *
     * @return the HTTP status code
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the business error code from the response envelope, if any.
     *
     * @return the error code, or {@code null}
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the raw decoded response body, if available.
     *
     * @return the response body, or {@code null}
     */
    public String getBody() {
        return body;
    }
}
