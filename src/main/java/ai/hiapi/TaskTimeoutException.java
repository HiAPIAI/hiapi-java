package ai.hiapi;

/**
 * {@code error_code=TASK_TIMEOUT} — the upstream task timed out; retryable.
 */
public class TaskTimeoutException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new task-timeout exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public TaskTimeoutException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
