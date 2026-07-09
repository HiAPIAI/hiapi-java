package ai.hiapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link Transport#retryAfterOrBackoff}: Retry-After parsing. */
class TransportTest {

    @Test
    void numericRetryAfterHonoured() {
        assertEquals(2.0, Transport.retryAfterOrBackoff(Optional.of("2"), 0));
    }

    @Test
    void negativeRetryAfterClampedToZero() {
        assertEquals(0.0, Transport.retryAfterOrBackoff(Optional.of("-5"), 0));
    }

    @Test
    void hugeRetryAfterClampedToMax() {
        assertEquals(60.0, Transport.retryAfterOrBackoff(Optional.of("9999"), 0));
    }

    @Test
    void httpDateRetryAfterHonoured() {
        // Retry-After may be an HTTP-date (RFC 7231), not just numeric seconds.
        String date = ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(30)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        double delay = Transport.retryAfterOrBackoff(Optional.of(date), 0);
        // ~30s until the date; slack for clock/rounding. A numeric-only parser
        // would instead fall back to backoff (0.5s on attempt 0).
        assertTrue(delay >= 25.0 && delay <= 31.0, "expected ~30s, got " + delay);
    }

    @Test
    void pastHttpDateClampedToZero() {
        String date = ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(60)
                .format(DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(0.0, Transport.retryAfterOrBackoff(Optional.of(date), 0));
    }

    @Test
    void unparseableRetryAfterFallsBackToBackoff() {
        // Neither a number nor an HTTP-date → exponential backoff 0.5 * 2^attempt.
        assertEquals(0.5, Transport.retryAfterOrBackoff(Optional.of("soon"), 0));
        assertEquals(1.0, Transport.retryAfterOrBackoff(Optional.of("later"), 1));
    }

    @Test
    void absentRetryAfterUsesBackoff() {
        assertEquals(0.5, Transport.retryAfterOrBackoff(Optional.empty(), 0));
        assertEquals(2.0, Transport.retryAfterOrBackoff(Optional.empty(), 2));
    }
}
