package ai.hiapi;

import java.util.Map;

/**
 * Immutable options for {@link Tasks#create(String, Map, CreateOptions)}.
 *
 * <p>Construct via {@link #builder()}. All fields are optional: {@code callback},
 * {@code route} and {@code idempotencyKey} default to {@code null}.</p>
 */
public final class CreateOptions {

    private final Map<String, Object> callback;
    private final String route;
    private final String idempotencyKey;

    private CreateOptions(Builder b) {
        this.callback = b.callback;
        this.route = b.route;
        this.idempotencyKey = b.idempotencyKey;
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
     * Creates a new builder.
     *
     * @return a fresh {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CreateOptions}.
     */
    public static final class Builder {
        private Map<String, Object> callback = null;
        private String route = null;
        private String idempotencyKey = null;

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
         * Selects a model route (e.g. {@code "pro"}) without writing the
         * {@code model@route} suffix form: {@code model "x"} + {@code route "pro"}
         * is the preferred spelling of {@code model "x@pro"}. {@code null}/"default"
         * both mean the default route. An unknown route is rejected with a 400
         * listing the available routes.
         *
         * @param route the route key, or {@code null} for the default route
         * @return this builder
         */
        public Builder route(String route) {
            this.route = route;
            return this;
        }

        /**
         * Sets the idempotency key (sent as the {@code Idempotency-Key} header, at
         * most 255 bytes). It makes retries safe: resending the same key + body
         * returns the original task ({@link CreatedTask#isIdempotentReplay()} is then
         * {@code true}) instead of creating and billing a second one. The transport
         * also becomes willing to retry the POST on network errors. Reusing a key
         * with a <em>different</em> body throws {@link IdempotencyKeyMismatchException}.
         *
         * @param idempotencyKey a stable key derived per logical job, or {@code null}
         * @return this builder
         */
        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        /**
         * Builds an immutable {@link CreateOptions}.
         *
         * @return the built options
         */
        public CreateOptions build() {
            return new CreateOptions(this);
        }
    }
}
