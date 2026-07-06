package ai.hiapi;

/**
 * {@code error_code=STORAGE_UNAVAILABLE} — output storage error; retryable.
 */
public class StorageUnavailableException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new storage-unavailable exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public StorageUnavailableException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
