package ai.hiapi;

/**
 * Raised when a task being polled (by {@code run} / {@code waitFor}) reaches the
 * terminal {@code fail} status.
 *
 * <p>This is distinct from a synchronous {@code error_code=TASK_FAILED}
 * rejection, which is surfaced as a plain {@link APIException}.</p>
 */
public class TaskFailedException extends HiAPIException {

    private static final long serialVersionUID = 1L;

    private final Task task;
    private final String code;

    /**
     * Creates a new task-failed exception, deriving its message and code from the
     * failed task's {@link TaskError}.
     *
     * @param task the full task in its failed state
     */
    public TaskFailedException(Task task) {
        super(buildMessage(task));
        this.task = task;
        TaskError err = task == null ? null : task.getError();
        this.code = err == null ? null : err.getCode();
    }

    private static String buildMessage(Task task) {
        String taskId = task == null ? "?" : task.getTaskId();
        if (taskId == null) {
            taskId = "?";
        }
        String message = null;
        if (task != null) {
            TaskError err = task.getError();
            if (err != null) {
                message = err.getMessage();
            }
        }
        if (message == null || message.isEmpty()) {
            message = "task failed";
        }
        return "task " + taskId + " failed: " + message;
    }

    /**
     * Returns the full task in its failed state.
     *
     * @return the failed task
     */
    public Task getTask() {
        return task;
    }

    /**
     * Returns the task {@code error.code}, if present.
     *
     * @return the error code, or {@code null}
     */
    public String getCode() {
        return code;
    }
}
