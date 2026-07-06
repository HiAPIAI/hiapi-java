package ai.hiapi;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Zero-dependency HTTP transport built on {@link java.net.http.HttpClient}.
 *
 * <p>Handles JSON request/response framing, authentication, retries with
 * exponential backoff (honouring {@code Retry-After}), and mapping non-2xx
 * responses onto the typed exception hierarchy.
 *
 * <p>Package-private: callers go through {@link Tasks} / {@link Webhooks}.
 */
class Transport {

    /**
     * Statuses worth retrying: rate limiting and transient platform
     * unavailability. These mean the server rejected the request <em>before</em>
     * processing, so retrying is safe even for a non-idempotent POST (no
     * duplicate task is created).
     */
    private static final Set<Integer> RETRY_STATUSES = Set.of(429, 503);

    /**
     * Methods safe to retry after a network error, where the outcome is unknown.
     * A {@code POST /tasks} must NOT be retried on a network error — it might
     * have created a task the SDK never saw the response for, double-charging
     * the account. The exception: a request carrying an {@code Idempotency-Key}
     * header IS safe to retry (the server collapses duplicates of the same
     * key+body into the first task).
     */
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("GET", "HEAD", "PUT", "DELETE");

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    /**
     * error_code of the retryable 409 the server returns while the first request
     * with the same Idempotency-Key is still in flight (honours Retry-After).
     */
    private static final String IDEMPOTENCY_PROCESSING_CODE = "IDEMPOTENCY_KEY_PROCESSING";

    private static final double MAX_BACKOFF_SECONDS = 8.0;
    private static final double MAX_RETRY_AFTER_SECONDS = 60.0;

    /** A parsed JSON envelope plus the response headers it arrived with. */
    static final class Result {
        final Map<String, Object> envelope;
        final java.net.http.HttpHeaders headers;

        Result(Map<String, Object> envelope, java.net.http.HttpHeaders headers) {
            this.envelope = envelope;
            this.headers = headers;
        }
    }

    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final HttpClient client;

    /**
     * Creates a transport.
     *
     * @param apiKey     the API key sent as a {@code Bearer} token
     * @param baseUrl    the API base URL; normalised to end in {@code /v1}
     * @param timeout    per-request timeout
     * @param maxRetries maximum number of retries (clamped to {@code >= 0})
     */
    Transport(String apiKey, String baseUrl, Duration timeout, int maxRetries) {
        this.apiKey = apiKey;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.timeout = timeout;
        this.maxRetries = Math.max(0, maxRetries);
        // A dedicated client built once and reused across requests.
        this.client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    // -- public ---------------------------------------------------------------

    /**
     * Performs a request and returns the parsed JSON envelope (a map).
     *
     * @param method HTTP method (GET/POST/...)
     * @param path   request path; a leading {@code /} is added if missing
     * @param body   request body to JSON-encode, or {@code null} for no body
     * @param params query parameters; {@code null} values are skipped
     * @return the parsed JSON object (empty map if the body is blank)
     * @throws APIException           on non-2xx responses or non-object bodies
     * @throws APIConnectionException on unrecoverable network failure
     */
    Map<String, Object> request(String method, String path, Map<String, Object> body, Map<String, Object> params) {
        return requestWithHeaders(method, path, body, params, null).envelope;
    }

    /**
     * Like {@link #request}, but with optional extra request headers, returning
     * the response headers alongside the parsed envelope.
     *
     * @param method       HTTP method (GET/POST/...)
     * @param path         request path; a leading {@code /} is added if missing
     * @param body         request body to JSON-encode, or {@code null} for no body
     * @param params       query parameters; {@code null} values are skipped
     * @param extraHeaders additional request headers, or {@code null}
     * @return the parsed JSON envelope plus the response headers
     * @throws APIException           on non-2xx responses or non-object bodies
     * @throws APIConnectionException on unrecoverable network failure
     */
    Result requestWithHeaders(String method, String path, Map<String, Object> body,
            Map<String, Object> params, Map<String, String> extraHeaders) {
        String url = buildUrl(path, params);
        String data = body != null ? Json.write(body) : null;
        // An Idempotency-Key makes the POST safe to retry: the server collapses
        // duplicates of the same key+body into the first task.
        boolean hasIdempotencyKey = extraHeaders != null
                && extraHeaders.get(IDEMPOTENCY_KEY_HEADER) != null
                && !extraHeaders.get(IDEMPOTENCY_KEY_HEADER).isEmpty();
        boolean retriableMethod = IDEMPOTENT_METHODS.contains(method.toUpperCase()) || hasIdempotencyKey;

        int attempt = 0;
        while (true) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Accept", "application/json")
                    .header("User-Agent", "hiapi-java/" + HiAPI.VERSION);

            if (extraHeaders != null) {
                for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }

            if (data != null) {
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(data, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpRequest httpRequest = builder.build();
            try {
                HttpResponse<String> response =
                        client.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return new Result(decodeJson(response.body(), status), response.headers());
                }
                if (RETRY_STATUSES.contains(status) && attempt < maxRetries) {
                    sleep(retryDelay(attempt, response));
                    attempt++;
                    continue;
                }
                APIException apiExc = errorFromResponse(status, response.body());
                // A 409 IDEMPOTENCY_KEY_PROCESSING means the first request with
                // this key is still in flight; honour Retry-After and retry —
                // the next attempt hits the replay path and returns the task.
                if (hasIdempotencyKey && status == 409
                        && IDEMPOTENCY_PROCESSING_CODE.equals(apiExc.getErrorCode())
                        && attempt < maxRetries) {
                    sleep(retryDelay(attempt, response));
                    attempt++;
                    continue;
                }
                throw apiExc;
            } catch (IOException exc) {
                // Connect/read timeouts or TLS/reset errors. Only retry network
                // failures for idempotent methods (or a POST carrying an
                // Idempotency-Key); a blindly retried POST could silently create
                // a second task.
                if (retriableMethod && attempt < maxRetries) {
                    sleep(retryDelay(attempt, null));
                    attempt++;
                    continue;
                }
                throw new APIConnectionException("request to " + url + " failed: " + exc.getMessage(), exc);
            } catch (InterruptedException exc) {
                // Restore the interrupt flag so callers can observe cancellation,
                // then surface as a connection failure rather than retrying (the
                // interrupt flag is set, so any backoff sleep would re-throw).
                Thread.currentThread().interrupt();
                throw new APIConnectionException("request to " + url + " was interrupted", exc);
            }
        }
    }

    // -- internals ------------------------------------------------------------

    private String buildUrl(String path, Map<String, Object> params) {
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        String url = baseUrl + path;
        if (params != null && !params.isEmpty()) {
            StringBuilder query = new StringBuilder();
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Object value = entry.getValue();
                if (value == null) {
                    continue;
                }
                if (query.length() > 0) {
                    query.append('&');
                }
                query.append(urlEncode(entry.getKey()))
                        .append('=')
                        .append(urlEncode(String.valueOf(value)));
            }
            if (query.length() > 0) {
                url = url + "?" + query;
            }
        }
        return url;
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private double retryDelay(int attempt, HttpResponse<String> response) {
        if (response != null) {
            Optional<String> retryAfter = response.headers().firstValue("Retry-After");
            if (retryAfter.isPresent() && !retryAfter.get().isBlank()) {
                try {
                    double seconds = Double.parseDouble(retryAfter.get().trim());
                    // Clamp to [0, max]: a negative Retry-After would make
                    // Thread.sleep() misbehave or break the retry loop.
                    return Math.max(0.0, Math.min(seconds, MAX_RETRY_AFTER_SECONDS));
                } catch (NumberFormatException ignored) {
                    // HTTP-date form: fall back to exponential backoff.
                }
            }
        }
        return Math.min(0.5 * Math.pow(2, attempt), MAX_BACKOFF_SECONDS);
    }

    private static void sleep(double seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep((long) (seconds * 1000));
        } catch (InterruptedException exc) {
            // Restore the interrupt flag; abandon the backoff and let the next
            // loop iteration / caller observe the interruption.
            Thread.currentThread().interrupt();
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!base.endsWith("/v1")) {
            base += "/v1";
        }
        return base;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decodeJson(String raw, int status) {
        if (raw == null || raw.isEmpty()) {
            return new HashMap<>();
        }
        Object parsed;
        try {
            parsed = Json.parse(raw);
        } catch (IllegalArgumentException exc) {
            throw new APIException(
                    "could not decode response body as JSON: " + exc.getMessage(),
                    status,
                    null,
                    raw);
        }
        if (!(parsed instanceof Map)) {
            throw new APIException("expected a JSON object response", status, null, raw);
        }
        return (Map<String, Object>) parsed;
    }

    private static APIException errorFromResponse(int status, String raw) {
        String errorCode = null;
        String message = null;
        String bodyText = (raw == null || raw.isEmpty()) ? null : raw;

        if (raw != null && !raw.isEmpty()) {
            try {
                Object parsed = Json.parse(raw);
                if (parsed instanceof Map) {
                    Map<?, ?> map = (Map<?, ?>) parsed;
                    Object code = map.get("error_code");
                    if (code instanceof String) {
                        errorCode = (String) code;
                    }
                    Object msg = map.get("message");
                    if (msg instanceof String) {
                        message = (String) msg;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // Non-JSON error body: fall through with raw text only.
            }
        }

        String finalMessage = message != null ? message : "HTTP " + status;
        return buildException(finalMessage, status, errorCode, bodyText);
    }

    private static APIException buildException(String message, int status, String errorCode, String body) {
        // Map by error_code first, then fall back to status code.
        if (errorCode != null) {
            switch (errorCode) {
                case "INVALID_REQUEST":
                    return new InvalidRequestException(message, status, errorCode, body);
                case "MODEL_UNAVAILABLE":
                    return new ModelUnavailableException(message, status, errorCode, body);
                case "TASK_TIMEOUT":
                    return new TaskTimeoutException(message, status, errorCode, body);
                case "STORAGE_UNAVAILABLE":
                    return new StorageUnavailableException(message, status, errorCode, body);
                case "IDEMPOTENCY_KEY_PROCESSING":
                    return new IdempotencyKeyProcessingException(message, status, errorCode, body);
                case "IDEMPOTENCY_KEY_MISMATCH":
                    return new IdempotencyKeyMismatchException(message, status, errorCode, body);
                default:
                    break;
            }
        }
        switch (status) {
            case 400:
                return new InvalidRequestException(message, status, errorCode, body);
            case 401:
                return new AuthenticationException(message, status, errorCode, body);
            case 404:
                return new NotFoundException(message, status, errorCode, body);
            case 503:
                return new ServiceUnavailableException(message, status, errorCode, body);
            default:
                return new APIException(message, status, errorCode, body);
        }
    }
}
