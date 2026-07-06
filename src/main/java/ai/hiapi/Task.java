package ai.hiapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An async generation task and (when finished) its output.
 *
 * <p>Immutable. The wire format uses camelCase ({@code taskId}, {@code expireAt}) and the
 * epoch-second timestamp fields {@code created}/{@code completed}; this class exposes parsed
 * accessors and keeps the full payload in {@link #getRaw()} so a server-side addition never
 * breaks an older SDK.
 */
public final class Task {

    private final String taskId;
    private final String model;
    private final String status;
    private final Long createdAt;
    private final Long completedAt;
    private final String storage;
    private final String route;
    private final List<Output> output;
    private final TaskError error;
    private final Map<String, Object> raw;

    /**
     * Creates an immutable task.
     *
     * @param taskId      the task id
     * @param model       the model name
     * @param status      the task status (e.g. {@code queued}, {@code success}, {@code fail})
     * @param createdAt   creation time in epoch seconds, or {@code null} if absent
     * @param completedAt completion time in epoch seconds, or {@code null} if not yet completed
     * @param storage     the storage tier (e.g. {@code "temp"}), or {@code null} if absent
     * @param route       the route parameter echoed from submission, or {@code null} if absent
     * @param output      the generated artifacts (defensively copied; never {@code null})
     * @param error       the failure reason when {@code status} is {@code fail}, else {@code null}
     * @param raw         the original wire map (defensively copied)
     */
    public Task(String taskId,
                String model,
                String status,
                Long createdAt,
                Long completedAt,
                String storage,
                String route,
                List<Output> output,
                TaskError error,
                Map<String, Object> raw) {
        this.taskId = taskId;
        this.model = model;
        this.status = status;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.storage = storage;
        this.route = route;
        this.output = output == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(output));
        this.error = error;
        this.raw = raw == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(raw));
    }

    /** @return the task id (never {@code null}; empty string if missing) */
    public String getTaskId() {
        return taskId;
    }

    /** @return the model name (never {@code null}; empty string if missing) */
    public String getModel() {
        return model;
    }

    /** @return the task status (never {@code null}; empty string if missing) */
    public String getStatus() {
        return status;
    }

    /** @return creation time in epoch seconds, or {@code null} if absent */
    public Long getCreatedAt() {
        return createdAt;
    }

    /** @return completion time in epoch seconds, or {@code null} if not yet completed */
    public Long getCompletedAt() {
        return completedAt;
    }

    /** @return the storage tier (e.g. {@code "temp"}), or {@code null} if absent */
    public String getStorage() {
        return storage;
    }

    /**
     * The route parameter echoed only when the task was submitted with an explicit
     * {@code route}; {@link #getModel()} then holds the resolved full name
     * ({@code base@route}).
     *
     * @return the submitted route, or {@code null} when the task was submitted without one
     */
    public String getRoute() {
        return route;
    }

    /** @return an unmodifiable list of generated artifacts (never {@code null}) */
    public List<Output> getOutput() {
        return output;
    }

    /** @return the failure reason when failed, or {@code null} otherwise */
    public TaskError getError() {
        return error;
    }

    /** @return an unmodifiable view of the original wire map */
    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * @return {@code true} if the task has reached a terminal status ({@code success} or
     *         {@code fail}) and will never change again
     */
    public boolean isTerminal() {
        return "success".equals(status) || "fail".equals(status);
    }

    /** @return {@code true} if the task finished successfully ({@code status == "success"}) */
    public boolean isSucceeded() {
        return "success".equals(status);
    }

    /**
     * Builds a {@code Task} from a decoded JSON map.
     *
     * <p>Defensive: non-map entries in the {@code output} array are skipped, a missing
     * {@code error} object yields {@code null}, and the {@code completed: 0} sentinel the API
     * sends while a task is non-terminal is normalized to {@code null} so callers never read it
     * as epoch 0.
     *
     * @param d the decoded wire map (may contain extra fields, kept in {@code raw})
     * @return the parsed task
     */
    public static Task fromMap(Map<String, Object> d) {
        if (d == null) {
            d = Collections.emptyMap();
        }
        List<Output> output = new ArrayList<>();
        Object outRaw = d.get("output");
        if (outRaw instanceof List<?>) {
            for (Object o : (List<?>) outRaw) {
                if (o instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> om = (Map<String, Object>) o;
                    output.add(Output.fromMap(om));
                }
            }
        }

        TaskError error = null;
        Object errRaw = d.get("error");
        if (errRaw instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> em = (Map<String, Object>) errRaw;
            error = TaskError.fromMap(em);
        }

        // The API sends `completed: 0` (not omitted) while a task is non-terminal;
        // normalize that sentinel to null so callers don't read it as epoch 0.
        Long completed = Models.toLong(d.get("completed"));
        if (completed != null && completed == 0L) {
            completed = null;
        }

        Object taskId = d.get("taskId");
        Object model = d.get("model");
        Object status = d.get("status");
        Object storage = d.get("storage");
        Object route = d.get("route");

        return new Task(
                taskId == null ? "" : String.valueOf(taskId),
                model == null ? "" : String.valueOf(model),
                status == null ? "" : String.valueOf(status),
                Models.toLong(d.get("created")),
                completed,
                storage == null ? null : String.valueOf(storage),
                route == null ? null : String.valueOf(route),
                output,
                error,
                d);
    }
}
