package ai.hiapi;

/**
 * Callback invoked once per status change while waiting for a task to finish.
 *
 * <p>Receives the latest {@link Task} each time its {@code status} changes
 * during {@link Tasks#waitFor}/{@link Tasks#run} polling.</p>
 */
@FunctionalInterface
public interface OnUpdate {
    /**
     * Called with the latest task snapshot on each status change.
     *
     * @param task the most recently retrieved task
     */
    void onUpdate(Task task);
}
