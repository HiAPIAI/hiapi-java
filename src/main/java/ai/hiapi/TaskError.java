package ai.hiapi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The failure reason attached to a task in the {@code fail} state.
 *
 * <p>Immutable. Unknown/extra fields are preserved in {@link #getRaw()}.
 */
public final class TaskError {

    private final String code;
    private final String message;
    private final Map<String, Object> raw;

    /**
     * Creates an immutable task error.
     *
     * @param code    the machine-readable error code, or {@code null} if absent
     * @param message the human-readable error message, or {@code null} if absent
     * @param raw     the original wire map (defensively copied)
     */
    public TaskError(String code, String message, Map<String, Object> raw) {
        this.code = code;
        this.message = message;
        this.raw = raw == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(raw));
    }

    /** @return the machine-readable error code, or {@code null} if absent */
    public String getCode() {
        return code;
    }

    /** @return the human-readable error message, or {@code null} if absent */
    public String getMessage() {
        return message;
    }

    /** @return an unmodifiable view of the original wire map */
    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * Builds a {@code TaskError} from a decoded JSON map.
     *
     * @param d the decoded wire map (may contain extra fields, kept in {@code raw})
     * @return the parsed task error
     */
    public static TaskError fromMap(Map<String, Object> d) {
        if (d == null) {
            d = Collections.emptyMap();
        }
        Object code = d.get("code");
        Object message = d.get("message");
        return new TaskError(
                code == null ? null : String.valueOf(code),
                message == null ? null : String.valueOf(message),
                d);
    }
}
