package ai.hiapi;

import java.util.Map;

/**
 * Immutable options for {@link Tasks#run(String, Map, RunOptions)}.
 *
 * <p>Construct via {@link #builder()}. All fields are optional and carry
 * sensible defaults: {@code pollInterval} = 3.0s, {@code timeout} = 600.0s,
 * {@code callback} and {@code onUpdate} are {@code null}.</p>
 */
public final class RunOptions {

    /** Default poll interval in seconds. */
    public static final double DEFAULT_POLL_INTERVAL = 3.0;
    /** Default timeout in seconds. */
    public static final double DEFAULT_TIMEOUT = 600.0;

    private final Map<String, Object> callback;
    private final String route;
    private final String idempotencyKey;
    private final double pollInterval;
    private final double timeout;
    private final OnUpdate onUpdate;

    private RunOptions(Builder b) {
        this.callback = b.callback;
        this.route = b.route;
        this.idempotencyKey = b.idempotencyKey;
        this.pollInterval = b.pollInterval;
        this.timeout = b.timeout;
        this.onUpdate = b.onUpdate;
    }

    /**
     * Optional webhook callback configuration (e.g. {@code {"url": ..., "when": "final"}}),
     * or {@code null} for none.
     *
     * @return the callback map, or {@code null}
     */
    public Map<String, Object> getCallback() {
        return callback;
    }

    /**
     * The model route to use (e.g. {@code "pro"}), or {@code null} for the default route.
     *
     * @return the route, or {@code null}
     */
    public String getRoute() {
        return route;
    }

    /**
     * The idempotency key sent as the {@code Idempotency-Key} header, or {@code null}.
     *
     * @return the idempotency key, or {@code null}
     */
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /**
     * Poll interval in seconds.
     *
     * @return the poll interval
     */
    public double getPollInterval() {
        return pollInterval;
    }

    /**
     * Maximum time to wait in seconds.
     *
     * @return the timeout
     */
    public double getTimeout() {
        return timeout;
    }

    /**
     * Status-change callback, or {@code null}.
     *
     * @return the {@link OnUpdate} callback, or {@code null}
     */
    public OnUpdate getOnUpdate() {
        return onUpdate;
    }

    /**
     * Creates a new builder pre-populated with defaults.
     *
     * @return a fresh {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RunOptions}.
     */
    public static final class Builder {
        private Map<String, Object> callback = null;
        private String route = null;
        private String idempotencyKey = null;
        private double pollInterval = DEFAULT_POLL_INTERVAL;
        private double timeout = DEFAULT_TIMEOUT;
        private OnUpdate onUpdate = null;

        private Builder() {
        }

        /**
         * Sets the webhook callback configuration.
         *
         * @param callback callback map, or {@code null} for none
         * @return this builder
         */
        public Builder callback(Map<String, Object> callback) {
            this.callback = callback;
            return this;
        }

        /**
         * Selects a model route (e.g. {@code "pro"}); see {@link CreateOptions.Builder#route}.
         *
         * @param route the route key, or {@code null} for the default route
         * @return this builder
         */
        public Builder route(String route) {
            this.route = route;
            return this;
        }

        /**
         * Sets the idempotency key; see {@link CreateOptions.Builder#idempotencyKey}.
         *
         * @param idempotencyKey a stable key derived per logical job, or {@code null}
         * @return this builder
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * Sets the poll interval in seconds.
         *
         * @param pollInterval poll interval (must be {@code > 0} when run)
         * @return this builder
         */
        public Builder pollInterval(double pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        /**
         * Sets the timeout in seconds.
         *
         * @param timeout timeout (must be {@code >= 0} when run)
         * @return this builder
         */
        public Builder timeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the status-change callback.
         *
         * @param onUpdate callback, or {@code null}
         * @return this builder
         */
        public Builder onUpdate(OnUpdate onUpdate) {
            this.onUpdate = onUpdate;
            return this;
        }

        /**
         * Builds an immutable {@link RunOptions}.
         *
         * @return the built options
         */
        public RunOptions build() {
            return new RunOptions(this);
        }
    }
}
