package ai.hiapi;

/**
 * Internal parsing helpers shared by the model classes.
 *
 * <p>Package-private; not part of the public API.
 */
final class Models {

    private Models() {
    }

    /**
     * Null-safe conversion of a decoded JSON value to a {@link Long}.
     *
     * <p>JSON numbers arrive as {@link Double} from {@code Json.parse}; this coerces any
     * {@link Number} to {@code long}. A missing value (or non-numeric value) yields {@code null}.
     *
     * @param v the decoded value (typically {@link Double} or {@code null})
     * @return the value as a boxed {@code Long}, or {@code null} if absent/non-numeric
     */
    static Long toLong(Object v) {
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return null;
    }

    /**
     * Null-safe conversion of a decoded JSON value to an {@link Integer}.
     *
     * @param v the decoded value (typically {@link Double} or {@code null})
     * @return the value as a boxed {@code Integer}, or {@code null} if absent/non-numeric
     */
    static Integer toInteger(Object v) {
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        return null;
    }
}
