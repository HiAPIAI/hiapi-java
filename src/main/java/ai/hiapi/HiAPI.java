package ai.hiapi;

import java.time.Duration;

/**
 * Client for the HiAPI unified async task API ({@code /v1/tasks}) — the entry
 * point to the SDK.
 *
 * <p>Construct it with an API key and reach the resources through {@link #tasks()}
 * and {@link #webhooks()}:</p>
 *
 * <pre>
 *     HiAPI client = new HiAPI("sk-...");
 *     Task task = client.tasks().run(
 *             "seedance-2-0",
 *             java.util.Map.of("prompt", "a cyan glass data center", "resolution", "1080p"));
 *     System.out.println(task.getOutput().get(0).getUrl());
 * </pre>
 *
 * <p>For non-default base URL, timeout, retries, or a webhook signing secret, use
 * the {@link Builder}:</p>
 *
 * <pre>
 *     HiAPI client = HiAPI.builder()
 *             .apiKey("sk-...")
 *             .timeout(java.time.Duration.ofSeconds(120))
 *             .maxRetries(3)
 *             .webhookSecret("whsec_...")
 *             .build();
 * </pre>
 */
public final class HiAPI {

    /** The SDK version, surfaced in the {@code User-Agent} header. */
    public static final String VERSION = "0.2.0";

    /** Default API base URL, including the {@code /v1} prefix. */
    public static final String DEFAULT_BASE_URL = "https://api.hiapi.ai/v1";

    /** Default per-request timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    /** Default number of retries for 429/503 and idempotent network errors. */
    public static final int DEFAULT_MAX_RETRIES = 2;

    /** Environment variable consulted for the API key when none is passed. */
    private static final String API_KEY_ENV = "HIAPI_API_KEY";

    /** Environment variable consulted for the webhook secret when none is passed. */
    private static final String WEBHOOK_SECRET_ENV = "HIAPI_WEBHOOK_SECRET";

    private final String baseUrl;
    private final Tasks tasks;
    private final Webhooks webhooks;

    /**
     * Creates a client with the default base URL, timeout, and retry settings.
     *
     * @param apiKey the account API key ({@code sk-...}); when {@code null} the
     *               {@code HIAPI_API_KEY} environment variable is used instead
     * @throws HiAPIException if no API key is supplied and the environment
     *                        variable is unset or blank
     */
    public HiAPI(String apiKey) {
        this(resolveApiKey(apiKey),
                DEFAULT_BASE_URL,
                DEFAULT_TIMEOUT,
                DEFAULT_MAX_RETRIES,
                envOrNull(WEBHOOK_SECRET_ENV));
    }

    /**
     * Internal constructor used by both the public constructor and the
     * {@link Builder}. All inputs are assumed already resolved/defaulted.
     *
     * @param apiKey        the resolved, non-blank API key
     * @param baseUrl       the API base URL (normalised by {@link Transport})
     * @param timeout       the per-request timeout
     * @param maxRetries    the maximum number of retries
     * @param webhookSecret the default webhook signing secret, or {@code null}
     */
    private HiAPI(String apiKey, String baseUrl, Duration timeout, int maxRetries, String webhookSecret) {
        Transport transport = new Transport(apiKey, baseUrl, timeout, maxRetries);
        this.baseUrl = baseUrl;
        this.tasks = new Tasks(transport);
        this.webhooks = new Webhooks(webhookSecret);
    }

    /**
     * Returns the {@code tasks} resource for creating, retrieving, listing, and
     * running tasks.
     *
     * @return the bound {@link Tasks} resource
     */
    public Tasks tasks() {
        return tasks;
    }

    /**
     * Returns the {@code webhooks} helper for verifying callback signatures.
     *
     * @return the bound {@link Webhooks} helper
     */
    public Webhooks webhooks() {
        return webhooks;
    }

    /**
     * Returns the API base URL this client was configured with.
     *
     * @return the base URL string
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Starts building a customised client.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves the API key, falling back to the {@code HIAPI_API_KEY} environment
     * variable, and validates that one is present.
     *
     * @param apiKey the explicitly supplied API key, or {@code null}
     * @return the resolved, non-blank API key
     * @throws HiAPIException if no usable API key can be found
     */
    private static String resolveApiKey(String apiKey) {
        String key = (apiKey != null && !apiKey.trim().isEmpty()) ? apiKey : envOrNull(API_KEY_ENV);
        if (key == null || key.trim().isEmpty()) {
            throw new HiAPIException(
                    "missing API key: pass apiKey to the HiAPI constructor/builder "
                            + "or set the HIAPI_API_KEY environment variable");
        }
        return key;
    }

    /**
     * Reads an environment variable, returning {@code null} when it is unset or
     * blank.
     *
     * @param name the environment variable name
     * @return the trimmed-non-blank value, or {@code null}
     */
    private static String envOrNull(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value;
    }

    /**
     * Fluent builder for {@link HiAPI}, allowing the base URL, timeout, retry
     * count, and webhook secret to be customised.
     */
    public static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = DEFAULT_TIMEOUT;
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private String webhookSecret;

        /** Creates a builder seeded with the SDK defaults. */
        Builder() {
        }

        /**
         * Sets the account API key. When left {@code null}, {@link #build()} falls
         * back to the {@code HIAPI_API_KEY} environment variable.
         *
         * @param apiKey the API key ({@code sk-...}), or {@code null}
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the API base URL (including the {@code /v1} prefix). A {@code null}
         * value resets it to the default.
         *
         * @param baseUrl the base URL, or {@code null} for the default
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = (baseUrl != null) ? baseUrl : DEFAULT_BASE_URL;
            return this;
        }

        /**
         * Sets the per-request timeout. A {@code null} value resets it to the
         * default.
         *
         * @param timeout the timeout, or {@code null} for the default
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = (timeout != null) ? timeout : DEFAULT_TIMEOUT;
            return this;
        }

        /**
         * Sets the maximum number of retries for 429/503 responses and idempotent
         * network errors.
         *
         * @param maxRetries the retry count (negative values are clamped to 0 by
         *                   the transport)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the default webhook signing secret. When left {@code null},
         * {@link #build()} falls back to the {@code HIAPI_WEBHOOK_SECRET}
         * environment variable.
         *
         * @param webhookSecret the signing key, or {@code null}
         * @return this builder
         */
        public Builder webhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
            return this;
        }

        /**
         * Builds the configured client.
         *
         * @return a new {@link HiAPI} instance
         * @throws HiAPIException if no API key was supplied and the
         *                        {@code HIAPI_API_KEY} environment variable is unset
         *                        or blank
         */
        public HiAPI build() {
            String resolvedKey = resolveApiKey(apiKey);
            String resolvedSecret = (webhookSecret != null) ? webhookSecret : envOrNull(WEBHOOK_SECRET_ENV);
            return new HiAPI(resolvedKey, baseUrl, timeout, maxRetries, resolvedSecret);
        }
    }
}
