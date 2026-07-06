package ai.hiapi;

/**
 * A network-level failure (DNS, connection reset, timeout) prevented the
 * request from completing.
 */
public class APIConnectionException extends HiAPIException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new connection exception.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying I/O cause, may be {@code null}
     */
    public APIConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
