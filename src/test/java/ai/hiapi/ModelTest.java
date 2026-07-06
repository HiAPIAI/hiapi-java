package ai.hiapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Contract tests pinning the model parsers to the documented {@code /v1/tasks}
 * wire fields ({@code created}/{@code completed}/{@code storage}/{@code tasks}/
 * {@code expireAt}). These break first if the wire contract drifts.
 */
class ModelTest {

    private static Map<String, Object> outputItem(String url, String type, long expireAt) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("url", url);
        o.put("type", type);
        o.put("expireAt", (double) expireAt);
        return o;
    }

    @Test
    void taskFromMapParsesRealSuccessFields() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", "tk-hiapi-abc123");
        data.put("model", "seedance-2-0");
        data.put("status", "success");
        data.put("created", 1777800499.0);
        data.put("completed", 1777800799.0);
        data.put("storage", "temp");
        List<Object> output = new ArrayList<>();
        output.add(outputItem("https://cdn.hiapi.ai/tasks/tk-hiapi-abc123/0.mp4", "video", 1777887199L));
        data.put("output", output);

        Task task = Task.fromMap(data);

        assertEquals("tk-hiapi-abc123", task.getTaskId());
        assertEquals("seedance-2-0", task.getModel());
        assertEquals("success", task.getStatus());
        assertTrue(task.isTerminal());
        assertTrue(task.isSucceeded());
        assertEquals(Long.valueOf(1777800499L), task.getCreatedAt());
        assertEquals(Long.valueOf(1777800799L), task.getCompletedAt());
        assertEquals("temp", task.getStorage());

        assertEquals(1, task.getOutput().size());
        Output out = task.getOutput().get(0);
        assertEquals("video", out.getType());
        assertTrue(out.getUrl().endsWith("0.mp4"));
        assertEquals(Long.valueOf(1777887199L), out.getExpireAt());
        assertNull(task.getError());
    }

    @Test
    void completedZeroNormalizesToNull() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", "t");
        data.put("model", "m");
        data.put("status", "handling");
        data.put("created", 1777800499.0);
        data.put("completed", 0.0);

        Task task = Task.fromMap(data);

        assertEquals(Long.valueOf(1777800499L), task.getCreatedAt());
        assertNull(task.getCompletedAt());
        assertFalse(task.isTerminal());
        assertFalse(task.isSucceeded());
    }

    @Test
    void allDocumentedStatusesRecognized() {
        for (String status : Arrays.asList("queued", "handling", "archiving", "success", "fail")) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("taskId", "t");
            data.put("model", "m");
            data.put("status", status);
            Task task = Task.fromMap(data);
            boolean expectedTerminal = status.equals("success") || status.equals("fail");
            assertEquals(expectedTerminal, task.isTerminal(), "terminal for " + status);
        }
    }

    @Test
    void failTaskExposesError() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", "tk-hiapi-abc123");
        data.put("model", "seedance-2-0");
        data.put("status", "fail");
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "TASK_FAILED");
        error.put("message", "upstream rejected the prompt");
        data.put("error", error);

        Task task = Task.fromMap(data);

        assertEquals("fail", task.getStatus());
        assertTrue(task.isTerminal());
        assertFalse(task.isSucceeded());
        assertNotNull(task.getError());
        assertEquals("TASK_FAILED", task.getError().getCode());
        assertEquals("upstream rejected the prompt", task.getError().getMessage());
        assertTrue(task.getOutput().isEmpty());
    }

    @Test
    void malformedOutputItemsAreSkipped() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", "t");
        data.put("model", "m");
        data.put("status", "success");
        List<Object> output = new ArrayList<>();
        output.add(null);
        output.add("oops");
        output.add(outputItem("https://cdn/x.png", "image", 1L));
        data.put("output", output);

        Task task = Task.fromMap(data);

        assertEquals(1, task.getOutput().size());
        assertEquals("https://cdn/x.png", task.getOutput().get(0).getUrl());
    }

    @Test
    void rawPayloadIsPreserved() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", "t");
        data.put("status", "queued");
        data.put("futureField", "kept");

        Task task = Task.fromMap(data);
        assertEquals("kept", task.getRaw().get("futureField"));
    }

    @Test
    void createdTaskFromMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("taskId", "tk-hiapi-01HZTQ8BX2N3GM3YFK4Z9D7VQR");

        CreatedTask created = CreatedTask.fromMap(data);
        assertEquals("tk-hiapi-01HZTQ8BX2N3GM3YFK4Z9D7VQR", created.getTaskId());
        assertEquals(35, created.getTaskId().length());
    }

    @Test
    void taskPageFromMapParsesTasksArray() {
        Map<String, Object> data = new LinkedHashMap<>();
        List<Object> tasks = new ArrayList<>();
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("taskId", "tk-1");
        one.put("model", "m");
        one.put("status", "success");
        tasks.add(one);
        Map<String, Object> two = new LinkedHashMap<>();
        two.put("taskId", "tk-2");
        two.put("model", "m");
        two.put("status", "handling");
        tasks.add(two);
        data.put("tasks", tasks);
        data.put("page", 2.0);
        data.put("size", 20.0);
        data.put("total", 41.0);

        TaskPage page = TaskPage.fromMap(data);

        assertEquals(Integer.valueOf(2), page.getPage());
        assertEquals(Integer.valueOf(20), page.getSize());
        assertEquals(Integer.valueOf(41), page.getTotal());
        assertEquals(2, page.getItems().size());
        assertEquals("tk-1", page.getItems().get(0).getTaskId());
        assertTrue(page.getItems().get(0).isSucceeded());
        assertEquals("tk-2", page.getItems().get(1).getTaskId());
        assertFalse(page.getItems().get(1).isTerminal());
    }

    @Test
    void taskPagePrefersTasksKeyOverFallbacks() {
        // Both "tasks" and "items" present: "tasks" wins per the contract.
        Map<String, Object> data = new LinkedHashMap<>();
        List<Object> tasks = new ArrayList<>();
        Map<String, Object> real = new LinkedHashMap<>();
        real.put("taskId", "from-tasks");
        real.put("status", "queued");
        tasks.add(real);
        data.put("tasks", tasks);

        List<Object> items = new ArrayList<>();
        Map<String, Object> decoy = new LinkedHashMap<>();
        decoy.put("taskId", "from-items");
        decoy.put("status", "queued");
        items.add(decoy);
        data.put("items", items);

        TaskPage page = TaskPage.fromMap(data);
        assertEquals(1, page.getItems().size());
        assertEquals("from-tasks", page.getItems().get(0).getTaskId());
    }
}
