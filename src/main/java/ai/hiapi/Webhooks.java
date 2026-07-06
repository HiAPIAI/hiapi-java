package ai.hiapi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Verifies HiAPI callback (webhook) signatures.
 *
 * <p>When a "Webhook signing key" is set in the HiAPI console, each terminal
 * callback carries two headers:
 *
 * <pre>
 *     X-HiAPI-Timestamp: Unix seconds (string)
 *     X-HiAPI-Signature: hex( HMAC_SHA256(secret, timestamp + "." + body) )
 * </pre>
 *
 * <p>{@link #verify} recomputes the signature over the <b>raw request body</b>
 * (never the re-serialized JSON, because re-encoding can change bytes and break
 * the check), guards against replay with a timestamp tolerance, and returns the
 * parsed {@link Task}.
 */
public final class Webhooks {

    /** Name of the timestamp header carried on every signed callback. */
    private static final String TIMESTAMP_HEADER = "X-HiAPI-Timestamp";

    /** Name of the signature header carried on every signed callback. */
    private static final String SIGNATURE_HEADER = "X-HiAPI-Signature";

    /** Default replay window in seconds; reject callbacks older/newer than this. */
    private static final int DEFAULT_TOLERANCE = 300;

    private final String defaultSecret;

    /**
     * Creates a webhook helper bound to a default signing secret.
     *
     * @param defaultSecret the webhook signing key configured in the HiAPI
     *                       console; may be {@code null} (a secret must then be
     *                       supplied to the {@code secret}-taking overload).
     */
    public Webhooks(String defaultSecret) {
        this.defaultSecret = defaultSecret;
    }

    /**
     * Verifies a callback using its request headers and the client's default secret.
     *
     * <p>Pulls {@code X-HiAPI-Timestamp} / {@code X-HiAPI-Signature} from
     * {@code headers} case-insensitively, and applies the default replay
     * tolerance of {@value #DEFAULT_TOLERANCE} seconds.
     *
     * @param body    the raw request body, exactly as received
     * @param headers the request headers (case-insensitive lookup)
     * @return the parsed {@link Task} carried by the callback
     * @throws WebhookVerificationException if verification fails for any reason
     */
    public Task verify(byte[] body, Map<String, String> headers) {
        return verify(body, headers, this.defaultSecret, DEFAULT_TOLERANCE);
    }

    /**
     * Verifies a callback using its request headers, an explicit secret and tolerance.
     *
     * <p>Pulls {@code X-HiAPI-Timestamp} / {@code X-HiAPI-Signature} from
     * {@code headers} case-insensitively.
     *
     * @param body             the raw request body, exactly as received
     * @param headers          the request headers (case-insensitive lookup)
     * @param secret           the webhook signing key to verify against
     * @param toleranceSeconds max allowed clock skew in seconds (0 disables the check)
     * @return the parsed {@link Task} carried by the callback
     * @throws WebhookVerificationException if verification fails for any reason
     */
    public Task verify(byte[] body, Map<String, String> headers, String secret, int toleranceSeconds) {
        Map<String, String> lowered = lowercaseKeys(headers);
        String timestamp = lowered.get(TIMESTAMP_HEADER.toLowerCase());
        String signature = lowered.get(SIGNATURE_HEADER.toLowerCase());
        return verifyWebhook(body, signature, timestamp, secret, toleranceSeconds, null);
    }

    /**
     * Verifies a raw callback against an explicit signature/timestamp/secret and
     * returns the parsed {@link Task}.
     *
     * <p>The signing scheme is verified against the backend and must match
     * exactly: {@code expected = lowercase-hex( HMAC_SHA256(secret_utf8,
     * timestamp_ascii + "." + body) )}, compared against the provided signature
     * in constant time.
     *
     * @param body             the raw request body, exactly as received
     * @param signature        value of the {@code X-HiAPI-Signature} header
     * @param timestamp        value of the {@code X-HiAPI-Timestamp} header
     * @param secret           the webhook signing key configured in the HiAPI console
     * @param toleranceSeconds max allowed clock skew in seconds (0 disables the check)
     * @param nowEpochSeconds  override for the current Unix time (for testing); when
     *                         {@code null} the system clock is used
     * @return the parsed {@link Task} carried by the callback
     * @throws WebhookVerificationException on missing/blank headers, a bad or stale
     *                                      timestamp, a signature mismatch, or a body
     *                                      that is not valid JSON
     */
    public static Task verifyWebhook(
            byte[] body,
            String signature,
            String timestamp,
            String secret,
            int toleranceSeconds,
            Long nowEpochSeconds) {
        if (isBlank(signature) || isBlank(timestamp)) {
            throw new WebhookVerificationException("missing signature or timestamp header");
        }
        if (isBlank(secret)) {
            throw new WebhookVerificationException("no webhook signing secret configured");
        }

        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException exc) {
            throw new WebhookVerificationException("invalid timestamp header", exc);
        }

        if (toleranceSeconds > 0) {
            long now = (nowEpochSeconds != null) ? nowEpochSeconds : Instant.now().getEpochSecond();
            if (Math.abs(now - ts) > toleranceSeconds) {
                throw new WebhookVerificationException(
                        "timestamp outside the allowed tolerance (possible replay)");
            }
        }

        byte[] bodyBytes = (body != null) ? body : new byte[0];
        byte[] timestampBytes = timestamp.getBytes(StandardCharsets.US_ASCII);
        byte[] message = new byte[timestampBytes.length + 1 + bodyBytes.length];
        System.arraycopy(timestampBytes, 0, message, 0, timestampBytes.length);
        message[timestampBytes.length] = (byte) '.';
        System.arraycopy(bodyBytes, 0, message, timestampBytes.length + 1, bodyBytes.length);

        String expected = hmacSha256Hex(secret, message);

        // Constant-time comparison over the hex byte arrays to avoid timing leaks.
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        byte[] providedBytes = signature.getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expectedBytes, providedBytes)) {
            throw new WebhookVerificationException("signature mismatch");
        }

        Object payload;
        try {
            String json = new String(bodyBytes, StandardCharsets.UTF_8);
            payload = Json.parse(json);
        } catch (RuntimeException exc) {
            throw new WebhookVerificationException("verified body is not valid JSON", exc);
        }
        if (!(payload instanceof Map)) {
            throw new WebhookVerificationException("verified body is not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) payload;
        return Task.fromMap(map);
    }

    /**
     * Computes {@code lowercase-hex( HMAC_SHA256(key, message) )}.
     *
     * @param secret  the HMAC key, taken as raw UTF-8 bytes
     * @param message the message bytes to sign
     * @return the lowercase hex-encoded HMAC-SHA256 digest
     */
    private static String hmacSha256Hex(String secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int v = b & 0xff;
                sb.append(Character.forDigit(v >>> 4, 16));
                sb.append(Character.forDigit(v & 0x0f, 16));
            }
            return sb.toString();
        } catch (java.security.GeneralSecurityException exc) {
            throw new WebhookVerificationException("failed to compute webhook signature", exc);
        }
    }

    /**
     * Returns a copy of {@code headers} with every key lowercased, for
     * case-insensitive lookup. Null keys and the null map are tolerated.
     *
     * @param headers the source header map (may be {@code null})
     * @return a new map keyed by lowercased header names
     */
    private static Map<String, String> lowercaseKeys(Map<String, String> headers) {
        Map<String, String> out = new LinkedHashMap<>();
        if (headers == null) {
            return out;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (key != null) {
                out.put(key.toLowerCase(), entry.getValue());
            }
        }
        return out;
    }

    /**
     * Tests whether a string is null or contains only whitespace.
     *
     * @param s the string to test
     * @return {@code true} if {@code s} is null, empty, or whitespace-only
     */
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
