package ai.hiapi;

/**
 * Base class for every error raised by the HiAPI SDK.
 *
 * <p>All SDK exceptions are unchecked (they extend {@link RuntimeException}) so
 * callers are never forced to {@code try/catch}, mirroring the ergonomics of the
 * Python SDK.</p>
 */
public class HiAPIException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Creates a new exception with the given message.
     *
     * @param message human-readable description of the error
     */
    public HiAPIException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the given message and underlying cause.
     *
     * @param message human-readable description of the error
     * @param cause   the underlying cause, may be {@code null}
     */
    public HiAPIException(String message, Throwable cause) {
        super(message, cause);
    }
}
