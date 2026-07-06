package ai.hiapi;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@code client.tasks} resource — the unified async task API
 * ({@code /v1/tasks}).
 *
 * <p>Submit a task with {@link #create}, poll it with {@link #retrieve}/
 * {@link #waitFor}, or do both in one call with {@link #run}.</p>
 */
public final class Tasks {

    private final Transport transport;

    /**
     * Creates a tasks resource bound to the given transport.
     *
     * @param transport the HTTP transport used for all requests
     */
    Tasks(Transport transport) {
        this.transport = transport;
    }

    /**
     * Submits a task and returns immediately with its {@code taskId}.
     *
     * <p>{@code input} holds the model's business parameters (fields vary per
     * model). Do not put callback/webhook fields inside {@code input}; pass a
     * {@code callback} separately via the overload or the request is rejected.</p>
     *
     * @param model the model identifier
     * @param input the model's business parameters
     * @return the created task containing its {@code taskId}
     */
    public CreatedTask create(String model, Map<String, Object> input) {
        return create(model, input, CreateOptions.builder().build());
    }

    /**
     * Submits a task with an optional webhook callback and returns immediately
     * with its {@code taskId}.
     *
     * @param model    the model identifier
     * @param input    the model's business parameters
     * @param callback optional webhook callback configuration, or {@code null}
     * @return the created task containing its {@code taskId}
     */
    public CreatedTask create(String model, Map<String, Object> input, Map<String, Object> callback) {
        return create(model, input, CreateOptions.builder().callback(callback).build());
    }

    /**
     * Submits a task with full options and returns immediately with its
     * {@code taskId}.
     *
     * <p>{@code opts.route} selects a model route (e.g. {@code "pro"}) without
     * writing the {@code model@route} suffix form — the preferred spelling of
     * {@code model "x@pro"} is {@code model "x"} + {@code route "pro"}.
     * {@code opts.idempotencyKey} (sent as the {@code Idempotency-Key} header)
     * makes retries safe: resending the same key + body returns the original
     * task ({@link CreatedTask#isIdempotentReplay()} is then {@code true})
     * instead of creating and billing a second one.</p>
     *
     * @param model the model identifier
     * @param input the model's business parameters
     * @param opts  create options (callback, route, idempotency key)
     * @return the created task containing its {@code taskId}
     */
    public CreatedTask create(String model, Map<String, Object> input, CreateOptions opts) {
        if (opts == null) {
            opts = CreateOptions.builder().build();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        if (opts.getRoute() != null && !opts.getRoute().isEmpty()) {
            body.put("route", opts.getRoute());
        }
        body.put("input", input);
        if (opts.getCallback() != null) {
            body.put("callback", opts.getCallback());
        }
        Map<String, String> extraHeaders = null;
        if (opts.getIdempotencyKey() != null && !opts.getIdempotencyKey().isEmpty()) {
            extraHeaders = Map.of(Transport.IDEMPOTENCY_KEY_HEADER, opts.getIdempotencyKey());
        }
        Transport.Result result = transport.requestWithHeaders("POST", "/tasks", body, null, extraHeaders);
        boolean replay = result.headers != null
                && "true".equalsIgnoreCase(
                        result.headers.firstValue("Idempotent-Replay").orElse(""));
        return CreatedTask.fromMap(data(result.envelope), replay);
    }

    /**
     * Fetches a task's current status and (when {@code success}) its output.
     *
     * @param taskId the task identifier
     * @return the task's current state
     */
    public Task retrieve(String taskId) {
        Map<String, Object> env = transport.request("GET", "/tasks/" + encode(taskId), null, null);
        return Task.fromMap(data(env));
    }

    /**
     * Lists your tasks, newest first (paginated).
     *
     * @param page the 1-based page number
     * @param size the page size
     * @return a page of tasks
     */
    public TaskPage list(int page, int size) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("page", page);
        params.put("size", size);
        Map<String, Object> env = transport.request("GET", "/tasks", null, params);
        return TaskPage.fromMap(data(env));
    }

    /**
     * Polls {@code taskId} until it reaches a terminal state.
     *
     * <p>Returns the {@link Task} on {@code success}. Throws
     * {@link TaskFailedException} on {@code fail} and {@link PollTimeoutException}
     * only once {@code timeoutSeconds} have actually elapsed. When
     * {@code onUpdate} is non-{@code null} it is invoked once per status change.</p>
     *
     * @param taskId              the task identifier
     * @param pollIntervalSeconds the interval between polls in seconds (must be {@code > 0})
     * @param timeoutSeconds      the maximum time to wait in seconds (must be {@code >= 0})
     * @param onUpdate            status-change callback, or {@code null}
     * @return the succeeded task
     * @throws TaskFailedException  if the task reaches the {@code fail} state
     * @throws PollTimeoutException if the timeout elapses before a terminal state
     */
    public Task waitFor(String taskId, double pollIntervalSeconds, double timeoutSeconds, OnUpdate onUpdate) {
        if (pollIntervalSeconds <= 0) {
            throw new IllegalArgumentException("pollInterval must be > 0");
        }
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        long deadline = System.nanoTime() + (long) (timeoutSeconds * 1_000_000_000.0);
        String lastStatus = null;
        boolean seen = false;
        while (true) {
            Task task = retrieve(taskId);
            if (onUpdate != null && (!seen || !equalsNullable(task.getStatus(), lastStatus))) {
                lastStatus = task.getStatus();
                seen = true;
                onUpdate.onUpdate(task);
            }
            if (task.isSucceeded()) {
                return task;
            }
            if (task.isTerminal()) {
                // Terminal but not success => fail.
                throw new TaskFailedException(task);
            }
            double remaining = (deadline - System.nanoTime()) / 1_000_000_000.0;
            if (remaining <= 0) {
                throw new PollTimeoutException(taskId, timeoutSeconds);
            }
            // Sleep the smaller of the poll interval and the time left, so the
            // last poll lands right at the deadline rather than before it.
            double sleepSeconds = Math.min(pollIntervalSeconds, remaining);
            sleep(sleepSeconds);
        }
    }

    /**
     * Creates a task and blocks until it finishes, using default options
     * (poll interval 3.0s, timeout 600.0s, no callback, no update callback).
     *
     * @param model the model identifier
     * @param input the model's business parameters
     * @return the succeeded task
     */
    public Task run(String model, Map<String, Object> input) {
        return run(model, input, RunOptions.builder().build());
    }

    /**
     * Creates a task and blocks until it finishes — the one-call workflow.
     *
     * <p>Equivalent to {@link #create} followed by {@link #waitFor}. The
     * {@code callback} on {@code opts} is optional and independent of polling.</p>
     *
     * @param model the model identifier
     * @param input the model's business parameters
     * @param opts  run options (poll interval, timeout, callback, update callback)
     * @return the succeeded task
     */
    public Task run(String model, Map<String, Object> input, RunOptions opts) {
        CreatedTask created = create(model, input, CreateOptions.builder()
                .callback(opts.getCallback())
                .route(opts.getRoute())
                .idempotencyKey(opts.getIdempotencyKey())
                .build());
        return waitFor(created.getTaskId(), opts.getPollInterval(), opts.getTimeout(), opts.getOnUpdate());
    }

    /** Extracts the {@code data} object from a success envelope, or an empty map. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(Map<String, Object> env) {
        if (env == null) {
            return new LinkedHashMap<>();
        }
        Object d = env.get("data");
        if (d instanceof Map) {
            return (Map<String, Object>) d;
        }
        return new LinkedHashMap<>();
    }

    /** URL-encodes a single path segment (encodes everything, including '/'). */
    private static String encode(String taskId) {
        try {
            // URLEncoder targets form encoding; convert '+' to %20 and restore
            // characters that are legal unencoded in a path segment.
            String e = URLEncoder.encode(taskId, StandardCharsets.UTF_8.name());
            return e.replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            // UTF-8 is always supported; this should never happen.
            throw new IllegalStateException(ex);
        }
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static void sleep(double seconds) {
        long millis = (long) (seconds * 1000.0);
        int nanos = (int) ((seconds * 1_000_000_000.0) % 1_000_000.0);
        try {
            Thread.sleep(Math.max(0, millis), Math.max(0, Math.min(999_999, nanos)));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HiAPIException("Interrupted while waiting for task");
        }
    }
}
