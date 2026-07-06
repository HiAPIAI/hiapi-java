package ai.hiapi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The thin response of {@code POST /v1/tasks} — just the new task id.
 *
 * <p>Immutable. The full payload is preserved in {@link #getRaw()}.
 */
public final class CreatedTask {

    private final String taskId;
    private final boolean idempotentReplay;
    private final Map<String, Object> raw;

    /**
     * Creates an immutable created-task response.
     *
     * @param taskId           the new task id
     * @param idempotentReplay whether the server answered from the idempotency cache
     * @param raw              the original wire map (defensively copied)
     */
    public CreatedTask(String taskId, boolean idempotentReplay, Map<String, Object> raw) {
        this.taskId = taskId;
        this.idempotentReplay = idempotentReplay;
        this.raw = raw == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(raw));
    }

    /** @return the new task id (never {@code null}; empty string if missing) */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Whether the server answered from the idempotency cache: the request carried
     * an {@code Idempotency-Key} it had already fulfilled, {@link #getTaskId()} is
     * the task created by the first request, and no new task was created or billed.
     *
     * @return {@code true} when this response was an idempotent replay
     */
    public boolean isIdempotentReplay() {
        return idempotentReplay;
    }

    /** @return an unmodifiable view of the original wire map */
    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * Builds a {@code CreatedTask} from a decoded JSON map.
     *
     * @param d the decoded wire map (may contain extra fields, kept in {@code raw})
     * @return the parsed created-task response
     */
    public static CreatedTask fromMap(Map<String, Object> d) {
        return fromMap(d, false);
    }

    /**
     * Builds a {@code CreatedTask} from a decoded JSON map with the replay flag.
     *
     * @param d                the decoded wire map (may contain extra fields, kept in {@code raw})
     * @param idempotentReplay whether the response carried {@code Idempotent-Replay: true}
     * @return the parsed created-task response
     */
    public static CreatedTask fromMap(Map<String, Object> d, boolean idempotentReplay) {
        if (d == null) {
            d = Collections.emptyMap();
        }
        Object taskId = d.get("taskId");
        return new CreatedTask(taskId == null ? "" : String.valueOf(taskId), idempotentReplay, d);
    }
}
