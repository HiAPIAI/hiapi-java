package ai.hiapi;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * One generated artifact produced by a successful task.
 *
 * <p>Immutable. Unknown/extra fields from the wire are preserved in {@link #getRaw()}
 * so a server-side addition never breaks an older SDK.
 */
public final class Output {

    private final String url;
    private final String type;
    private final Long expireAt;
    private final Map<String, Object> raw;

    /**
     * Creates an immutable output artifact.
     *
     * @param url      the artifact URL
     * @param type     the artifact type (e.g. {@code "image"})
     * @param expireAt epoch seconds when the URL expires, or {@code null} if absent
     * @param raw      the original wire map (defensively copied)
     */
    public Output(String url, String type, Long expireAt, Map<String, Object> raw) {
        this.url = url;
        this.type = type;
        this.expireAt = expireAt;
        this.raw = raw == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(raw));
    }

    /** @return the artifact URL (never {@code null}; empty string if missing) */
    public String getUrl() {
        return url;
    }

    /** @return the artifact type (never {@code null}; empty string if missing) */
    public String getType() {
        return type;
    }

    /** @return epoch seconds when the URL expires, or {@code null} if absent */
    public Long getExpireAt() {
        return expireAt;
    }

    /** @return an unmodifiable view of the original wire map */
    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * Builds an {@code Output} from a decoded JSON map.
     *
     * @param d the decoded wire map (may contain extra fields, kept in {@code raw})
     * @return the parsed output
     */
    public static Output fromMap(Map<String, Object> d) {
        if (d == null) {
            d = Collections.emptyMap();
        }
        Object url = d.get("url");
        Object type = d.get("type");
        return new Output(
                url == null ? "" : String.valueOf(url),
                type == null ? "" : String.valueOf(type),
                Models.toLong(d.get("expireAt")),
                d);
    }
}
