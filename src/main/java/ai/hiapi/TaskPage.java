package ai.hiapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One page of {@code GET /v1/tasks} (newest first).
 *
 * <p>Immutable. The full payload is preserved in {@link #getRaw()}.
 */
public final class TaskPage {

    private final List<Task> items;
    private final Integer page;
    private final Integer size;
    private final Integer total;
    private final Map<String, Object> raw;

    /**
     * Creates an immutable page of tasks.
     *
     * @param items the tasks on this page (defensively copied; never {@code null})
     * @param page  the 1-based page number, or {@code null} if absent
     * @param size  the page size, or {@code null} if absent
     * @param total the total number of tasks across all pages, or {@code null} if absent
     * @param raw   the original wire map (defensively copied)
     */
    public TaskPage(List<Task> items, Integer page, Integer size, Integer total, Map<String, Object> raw) {
        this.items = items == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
        this.page = page;
        this.size = size;
        this.total = total;
        this.raw = raw == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(raw));
    }

    /** @return an unmodifiable list of tasks on this page (never {@code null}) */
    public List<Task> getItems() {
        return items;
    }

    /** @return the 1-based page number, or {@code null} if absent */
    public Integer getPage() {
        return page;
    }

    /** @return the page size, or {@code null} if absent */
    public Integer getSize() {
        return size;
    }

    /** @return the total number of tasks across all pages, or {@code null} if absent */
    public Integer getTotal() {
        return total;
    }

    /** @return an unmodifiable view of the original wire map */
    public Map<String, Object> getRaw() {
        return raw;
    }

    /**
     * Builds a {@code TaskPage} from a decoded JSON map.
     *
     * <p>The API returns the page array under {@code "tasks"}; common alternates
     * ({@code items}/{@code list}/{@code data}/{@code records}) are accepted in that priority
     * order so a paginated list never silently parses to empty. Defensive: non-map entries in
     * the chosen array are skipped.
     *
     * @param d the decoded wire map (may contain extra fields, kept in {@code raw})
     * @return the parsed page
     */
    public static TaskPage fromMap(Map<String, Object> d) {
        if (d == null) {
            d = Collections.emptyMap();
        }
        List<?> rawItems = null;
        for (String key : new String[] {"tasks", "items", "list", "data", "records"}) {
            Object value = d.get(key);
            if (value instanceof List<?>) {
                rawItems = (List<?>) value;
                break;
            }
        }

        List<Task> items = new ArrayList<>();
        if (rawItems != null) {
            for (Object t : rawItems) {
                if (t instanceof Map<?, ?>) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tm = (Map<String, Object>) t;
                    items.add(Task.fromMap(tm));
                }
            }
        }

        return new TaskPage(
                items,
                Models.toInteger(d.get("page")),
                Models.toInteger(d.get("size")),
                Models.toInteger(d.get("total")),
                d);
    }
}
