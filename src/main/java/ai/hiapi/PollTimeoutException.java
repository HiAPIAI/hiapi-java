package ai.hiapi;

/**
 * Raised when {@code run} / {@code waitFor} gives up before the task reaches a
 * terminal state, exceeding the client-side timeout.
 */
public class PollTimeoutException extends HiAPIException {

    private static final long serialVersionUID = 1L;

    private final String taskId;
    private final double timeoutSeconds;

    /**
     * Creates a new poll-timeout exception with a helpful message.
     *
     * @param taskId         the task that was being polled
     * @param timeoutSeconds the client-side timeout (seconds) that was exceeded
     */
    public PollTimeoutException(String taskId, double timeoutSeconds) {
        super("task " + taskId + " did not finish within " + formatSeconds(timeoutSeconds)
                + "s; it may still complete — poll tasks.retrieve(\"" + taskId + "\") later");
        this.taskId = taskId;
        this.timeoutSeconds = timeoutSeconds;
    }

    private static String formatSeconds(double seconds) {
        if (seconds == Math.rint(seconds) && !Double.isInfinite(seconds)) {
            return Long.toString((long) seconds);
        }
        return Double.toString(seconds);
    }

    /**
     * Returns the id of the task that was being polled.
     *
     * @return the task id
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * Returns the client-side timeout (seconds) that was exceeded.
     *
     * @return the timeout in seconds
     */
    public double getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
