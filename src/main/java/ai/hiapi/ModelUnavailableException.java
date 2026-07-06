package ai.hiapi;

/**
 * {@code error_code=MODEL_UNAVAILABLE} — retry shortly or switch models.
 */
public class ModelUnavailableException extends APIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new model-unavailable exception.
     *
     * @param message   human-readable message from the response
     * @param status    HTTP status code
     * @param errorCode business error code from the response envelope, or {@code null}
     * @param body      raw decoded response body, or {@code null}
     */
    public ModelUnavailableException(String message, int status, String errorCode, String body) {
        super(message, status, errorCode, body);
    }
}
