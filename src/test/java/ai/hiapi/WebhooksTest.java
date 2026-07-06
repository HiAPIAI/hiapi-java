package ai.hiapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Webhooks} signature verification: a valid signature returns
 * the parsed {@link Task}, while a tampered signature/body, a stale timestamp,
 * a missing header, or a wrong secret all raise {@link WebhookVerificationException}.
 *
 * <p>The static {@code verifyWebhook} overload is driven with a fixed
 * {@code nowEpoch} so the replay-window check is deterministic and never flaky.
 */
class WebhooksTest {

    private static final String SECRET = "whsec_test_key";
    private static final long NOW = 1777800499L;

    /** Unusual interior spacing so re-serialization would change the bytes. */
    private static byte[] makeBody() {
        return ("{\"taskId\":\"tk-1\",  \"model\":\"m\", \"status\":\"success\","
                + "\"output\":[{\"url\":\"https://cdn/x.mp4\",\"type\":\"video\",\"expireAt\":1}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Reference signature: lowercase hex HMAC-SHA256(secret, ts + "." + body). */
    private static String sign(byte[] body, String ts, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] tsBytes = ts.getBytes(StandardCharsets.US_ASCII);
            byte[] message = new byte[tsBytes.length + 1 + body.length];
            System.arraycopy(tsBytes, 0, message, 0, tsBytes.length);
            message[tsBytes.length] = (byte) '.';
            System.arraycopy(body, 0, message, tsBytes.length + 1, body.length);
            byte[] digest = mac.doFinal(message);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                int v = b & 0xff;
                sb.append(Character.forDigit(v >>> 4, 16));
                sb.append(Character.forDigit(v & 0x0f, 16));
            }
            return sb.toString();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void verifyOkReturnsTask() {
        byte[] body = makeBody();
        String ts = Long.toString(NOW);
        String sig = sign(body, ts, SECRET);

        Task task = Webhooks.verifyWebhook(body, sig, ts, SECRET, 300, NOW);

        assertEquals("tk-1", task.getTaskId());
        assertTrue(task.isSucceeded());
        assertEquals("https://cdn/x.mp4", task.getOutput().get(0).getUrl());
        assertEquals("video", task.getOutput().get(0).getType());
    }

    @Test
    void verifyUsesRawBytesNotReserialized() {
        // The signature is over the raw bytes (with odd spacing); it must still
        // validate even though a re-encode would change the byte sequence.
        byte[] body = makeBody();
        String reEncoded = Json.write(Json.parse(new String(body, StandardCharsets.UTF_8)));
        assertTrue(!reEncoded.equals(new String(body, StandardCharsets.UTF_8)),
                "re-serialized form should differ from the raw body");

        String ts = Long.toString(NOW);
        String sig = sign(body, ts, SECRET);
        Task task = Webhooks.verifyWebhook(body, sig, ts, SECRET, 300, NOW);
        assertEquals("tk-1", task.getTaskId());
    }

    @Test
    void tamperedSignatureRejected() {
        byte[] body = makeBody();
        String ts = Long.toString(NOW);
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, "deadbeef", ts, SECRET, 300, NOW));
    }

    @Test
    void tamperedBodyRejected() {
        byte[] body = makeBody();
        String ts = Long.toString(NOW);
        String good = sign(body, ts, SECRET);
        byte[] tampered = new byte[body.length + 1];
        System.arraycopy(body, 0, tampered, 0, body.length);
        tampered[body.length] = (byte) ' ';
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(tampered, good, ts, SECRET, 300, NOW));
    }

    @Test
    void staleTimestampRejected() {
        byte[] body = makeBody();
        // Timestamp 10_000s in the past, well outside the 300s window.
        long staleTs = NOW - 10_000L;
        String ts = Long.toString(staleTs);
        String sig = sign(body, ts, SECRET);
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, sig, ts, SECRET, 300, NOW));
    }

    @Test
    void missingSignatureHeaderRejected() {
        byte[] body = makeBody();
        String ts = Long.toString(NOW);
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, "", ts, SECRET, 300, NOW));
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, null, ts, SECRET, 300, NOW));
    }

    @Test
    void missingTimestampHeaderRejected() {
        byte[] body = makeBody();
        String sig = sign(body, Long.toString(NOW), SECRET);
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, sig, "", SECRET, 300, NOW));
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, sig, null, SECRET, 300, NOW));
    }

    @Test
    void wrongSecretRejected() {
        byte[] body = makeBody();
        String ts = Long.toString(NOW);
        String sig = sign(body, ts, SECRET);
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, sig, ts, "other_secret", 300, NOW));
    }

    @Test
    void missingSecretRejected() {
        byte[] body = makeBody();
        String ts = Long.toString(NOW);
        String sig = sign(body, ts, SECRET);
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, sig, ts, null, 300, NOW));
    }

    @Test
    void invalidTimestampHeaderRejected() {
        byte[] body = makeBody();
        String sig = sign(body, "not-a-number", SECRET);
        assertThrows(WebhookVerificationException.class,
                () -> Webhooks.verifyWebhook(body, sig, "not-a-number", SECRET, 300, NOW));
    }

    @Test
    void instanceVerifyReadsHeadersCaseInsensitively() {
        byte[] body = makeBody();
        String ts = Long.toString(NOW);
        String sig = sign(body, ts, SECRET);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("x-hiapi-timestamp", ts);
        headers.put("X-HiAPI-Signature", sig);

        // toleranceSeconds 0 disables the replay-window check so the fixed NOW
        // timestamp validates regardless of the wall clock.
        Webhooks webhooks = new Webhooks(SECRET);
        Task task = webhooks.verify(body, headers, SECRET, 0);
        assertEquals("tk-1", task.getTaskId());
    }

    @Test
    void instanceVerifyWithoutSecretRejected() {
        byte[] body = makeBody();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-HiAPI-Timestamp", "1");
        headers.put("X-HiAPI-Signature", "x");

        Webhooks webhooks = new Webhooks(null);
        assertThrows(WebhookVerificationException.class,
                () -> webhooks.verify(body, headers));
    }
}
