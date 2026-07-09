package ai.hiapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Transport-level tests for {@link Tasks}, driven by an in-process
 * {@link HttpServer} stub bound to an ephemeral loopback port. They verify the
 * request method/path/body, response parsing, error-envelope mapping, and the
 * polling loops of {@code waitFor}/{@code run}.
 */
class TasksTest {

    /** A captured inbound request. */
    private static final class Captured {
        final String method;
        final String path;
        final Map<String, String> headers;
        final String body;

        Captured(String method, String path, Map<String, String> headers, String body) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.body = body;
        }
    }

    /** A canned response: HTTP status + JSON body string + optional headers. */
    private static final class Response {
        final int status;
        final String json;
        final Map<String, String> headers;

        Response(int status, String json) {
            this(status, json, Map.of());
        }

        Response(int status, String json, Map<String, String> headers) {
            this.status = status;
            this.json = json;
            this.headers = headers;
        }
    }

    private HttpServer server;
    private final List<Captured> requests = new ArrayList<>();
    private volatile Function<Captured, Response> responder;

    private HiAPI client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new StubHandler());
        server.setExecutor(null);
        server.start();
        int port = server.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port + "/v1";

        client = HiAPI.builder()
                .apiKey("sk-test")
                .baseUrl(baseUrl)
                .maxRetries(2)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private final class StubHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String body = readAll(exchange.getRequestBody());
            // com.sun.net.httpserver canonicalizes header names (e.g. "User-agent"),
            // so capture them case-insensitively for stable lookups.
            Map<String, String> headers = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((k, v) -> {
                if (v != null && !v.isEmpty()) {
                    headers.put(k, v.get(0));
                }
            });
            Captured captured = new Captured(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    headers,
                    body);
            synchronized (requests) {
                requests.add(captured);
            }

            Response response = responder.apply(captured);
            byte[] payload = response.json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            response.headers.forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            exchange.sendResponseHeaders(response.status, payload.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(payload);
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int n;
        while ((n = in.read(chunk)) != -1) {
            buf.write(chunk, 0, n);
        }
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    /** A responder that returns each canned response in turn; the last repeats. */
    private void sequence(Response... responses) {
        AtomicInteger i = new AtomicInteger(0);
        responder = captured -> {
            int idx = Math.min(i.getAndIncrement(), responses.length - 1);
            return responses[idx];
        };
    }

    private static Response ok(String dataJson) {
        return new Response(200, "{\"code\":200,\"message\":\"success\",\"data\":" + dataJson + "}");
    }

    private static Response detail(String status, String extraFields) {
        StringBuilder data = new StringBuilder();
        data.append("{\"taskId\":\"tk-hiapi-abc123\",\"model\":\"seedance-2-0\",\"status\":\"")
                .append(status).append("\"");
        if (extraFields != null && !extraFields.isEmpty()) {
            data.append(",").append(extraFields);
        }
        data.append("}");
        return ok(data.toString());
    }

    private Captured lastRequest() {
        synchronized (requests) {
            return requests.get(requests.size() - 1);
        }
    }

    // -- tests ----------------------------------------------------------------

    @Test
    void createReturnsTaskIdAndSendsCorrectRequest() {
        sequence(ok("{\"taskId\":\"tk-hiapi-abc123\"}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "hi");
        CreatedTask created = client.tasks().create("seedance-2-0", input);

        assertEquals("tk-hiapi-abc123", created.getTaskId());

        Captured req = lastRequest();
        assertEquals("POST", req.method);
        assertEquals("/v1/tasks", req.path);
        assertEquals("Bearer sk-test", req.headers.get("Authorization"));
        assertTrue(req.headers.getOrDefault("User-Agent", "").startsWith("hiapi-java/"));

        Object parsedBody = Json.parse(req.body);
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) parsedBody;
        assertEquals("seedance-2-0", bodyMap.get("model"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sentInput = (Map<String, Object>) bodyMap.get("input");
        assertEquals("hi", sentInput.get("prompt"));
        // No callback was supplied, so the key must be absent.
        assertTrue(!bodyMap.containsKey("callback"));
    }

    @Test
    void createWithCallbackIncludesCallback() {
        sequence(ok("{\"taskId\":\"tk-hiapi-abc123\"}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        Map<String, Object> callback = new LinkedHashMap<>();
        callback.put("url", "https://e.com/cb");
        callback.put("when", "final");

        client.tasks().create("m", input, callback);

        Object parsedBody = Json.parse(lastRequest().body);
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) parsedBody;
        @SuppressWarnings("unchecked")
        Map<String, Object> sentCallback = (Map<String, Object>) bodyMap.get("callback");
        assertEquals("https://e.com/cb", sentCallback.get("url"));
        assertEquals("final", sentCallback.get("when"));
    }

    @Test
    void retrieveParsesDetailFields() {
        sequence(detail("success",
                "\"created\":1777800499,\"completed\":1777800799,\"storage\":\"temp\","
                        + "\"output\":[{\"url\":\"https://cdn/x.mp4\",\"type\":\"video\",\"expireAt\":1777887199}]"));

        Task task = client.tasks().retrieve("tk-hiapi-abc123");

        assertEquals("tk-hiapi-abc123", task.getTaskId());
        assertEquals("success", task.getStatus());
        assertTrue(task.isSucceeded());
        assertTrue(task.isTerminal());
        assertEquals(Long.valueOf(1777800499L), task.getCreatedAt());
        assertEquals(Long.valueOf(1777800799L), task.getCompletedAt());
        assertEquals("temp", task.getStorage());
        assertEquals("https://cdn/x.mp4", task.getOutput().get(0).getUrl());
        assertEquals("video", task.getOutput().get(0).getType());
        assertEquals(Long.valueOf(1777887199L), task.getOutput().get(0).getExpireAt());

        Captured req = lastRequest();
        assertEquals("GET", req.method);
        assertEquals("/v1/tasks/tk-hiapi-abc123", req.path);
    }

    @Test
    void listParsesPage() {
        responder = captured -> ok(
                "{\"tasks\":[{\"taskId\":\"tk-1\",\"model\":\"m\",\"status\":\"success\"}],"
                        + "\"page\":2,\"size\":20,\"total\":41}");

        TaskPage page = client.tasks().list(2, 20);

        assertEquals(Integer.valueOf(41), page.getTotal());
        assertEquals(Integer.valueOf(2), page.getPage());
        assertEquals(Integer.valueOf(20), page.getSize());
        assertEquals("tk-1", page.getItems().get(0).getTaskId());

        Captured req = lastRequest();
        assertEquals("GET", req.method);
        assertTrue(req.path.contains("page=2"), "path was " + req.path);
        assertTrue(req.path.contains("size=20"), "path was " + req.path);
    }

    @Test
    void errorEnvelopeMapsToInvalidRequestException() {
        sequence(new Response(400,
                "{\"code\":400,\"error_code\":\"INVALID_REQUEST\",\"message\":\"bad input\",\"data\":null}"));

        InvalidRequestException ex = assertThrows(InvalidRequestException.class,
                () -> client.tasks().retrieve("tk-x"));
        assertEquals(400, ex.getStatus());
        assertEquals("INVALID_REQUEST", ex.getErrorCode());
        assertEquals("bad input", ex.getMessage());
    }

    @Test
    void errorEnvelopeMapsModelUnavailable() {
        sequence(new Response(400,
                "{\"code\":400,\"error_code\":\"MODEL_UNAVAILABLE\",\"message\":\"nope\",\"data\":null}"));

        ModelUnavailableException ex = assertThrows(ModelUnavailableException.class,
                () -> client.tasks().retrieve("tk-x"));
        assertEquals(400, ex.getStatus());
        assertEquals("MODEL_UNAVAILABLE", ex.getErrorCode());
    }

    @Test
    void statusFallbacksWhenNoErrorCode() {
        sequence(new Response(401, "{\"code\":401,\"message\":\"unauthorized\",\"data\":null}"));
        assertThrows(AuthenticationException.class, () -> client.tasks().retrieve("tk-x"));

        sequence(new Response(404, "{\"code\":404,\"message\":\"not found\",\"data\":null}"));
        assertThrows(NotFoundException.class, () -> client.tasks().retrieve("tk-x"));
    }

    @Test
    void waitForPollsUntilSuccess() {
        sequence(detail("queued", ""), detail("handling", ""), detail("success", ""));

        List<String> seen = new ArrayList<>();
        Task task = client.tasks().waitFor("tk-hiapi-abc123", 0.01, 5.0, t -> seen.add(t.getStatus()));

        assertTrue(task.isSucceeded());
        assertEquals(List.of("queued", "handling", "success"), seen);
    }

    @Test
    void waitForRaisesOnFail() {
        sequence(detail("fail", "\"error\":{\"code\":\"TASK_FAILED\",\"message\":\"boom\"}"));

        TaskFailedException ex = assertThrows(TaskFailedException.class,
                () -> client.tasks().waitFor("tk-hiapi-abc123", 0.01, 5.0, null));
        assertEquals("TASK_FAILED", ex.getCode());
        assertEquals("fail", ex.getTask().getStatus());
    }

    @Test
    void waitForTimesOut() {
        responder = captured -> detail("handling", "");

        PollTimeoutException ex = assertThrows(PollTimeoutException.class,
                () -> client.tasks().waitFor("tk-hiapi-abc123", 0.01, 0.05, null));
        assertEquals("tk-hiapi-abc123", ex.getTaskId());
    }

    @Test
    void waitForRejectsBadArguments() {
        responder = captured -> detail("handling", "");
        assertThrows(IllegalArgumentException.class,
                () -> client.tasks().waitFor("tk-x", 0.0, 5.0, null));
        assertThrows(IllegalArgumentException.class,
                () -> client.tasks().waitFor("tk-x", 1.0, -1.0, null));
    }

    @Test
    void runCreatesThenWaits() {
        sequence(
                ok("{\"taskId\":\"tk-hiapi-abc123\"}"),
                detail("handling", ""),
                detail("success", ""));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "hi");
        RunOptions opts = RunOptions.builder().pollInterval(0.01).timeout(5.0).build();
        Task task = client.tasks().run("seedance-2-0", input, opts);

        assertTrue(task.isSucceeded());
        synchronized (requests) {
            assertEquals("POST", requests.get(0).method);
            assertEquals("/v1/tasks", requests.get(0).path);
            assertEquals("GET", requests.get(1).method);
        }
    }

    @Test
    void retriesOn503ThenSucceeds() {
        sequence(
                new Response(503, "{\"code\":503,\"message\":\"busy\",\"data\":null}"),
                ok("{\"taskId\":\"tk-hiapi-abc123\"}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        CreatedTask created = client.tasks().create("m", input);

        assertEquals("tk-hiapi-abc123", created.getTaskId());
        synchronized (requests) {
            assertEquals(2, requests.size()); // one retry
        }
    }

    // -- route parameter ------------------------------------------------------

    @Test
    void createWithRouteSendsRouteField() {
        sequence(ok("{\"taskId\":\"tk-hiapi-abc123\"}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        client.tasks().create("gpt-image-2/text-to-image", input,
                CreateOptions.builder().route("pro").build());

        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) Json.parse(lastRequest().body);
        assertEquals("pro", bodyMap.get("route"));
        assertEquals("gpt-image-2/text-to-image", bodyMap.get("model"));
    }

    @Test
    void createWithoutRouteOmitsField() {
        sequence(ok("{\"taskId\":\"tk-hiapi-abc123\"}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        client.tasks().create("m", input);

        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) Json.parse(lastRequest().body);
        assertTrue(!bodyMap.containsKey("route"));
    }

    @Test
    void retrieveParsesRouteEcho() {
        sequence(detail("success", "\"route\":\"pro\",\"model\":\"gpt-image-2/text-to-image@pro\""));

        Task task = client.tasks().retrieve("tk-hiapi-abc123");
        assertEquals("pro", task.getRoute());
    }

    @Test
    void retrieveRouteAbsentIsNull() {
        sequence(detail("success", null));
        assertEquals(null, client.tasks().retrieve("tk-hiapi-abc123").getRoute());
    }

    // -- Idempotency-Key ------------------------------------------------------

    @Test
    void createSendsIdempotencyKeyHeader() {
        sequence(ok("{\"taskId\":\"tk-hiapi-abc123\"}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        CreatedTask created = client.tasks().create("m", input,
                CreateOptions.builder().idempotencyKey("wf-1:submit").build());

        assertEquals("wf-1:submit", lastRequest().headers.get("Idempotency-Key"));
        assertTrue(!created.isIdempotentReplay());
    }

    @Test
    void createNetworkErrorRetriedOnlyWithIdempotencyKey() {
        // Drop the connection before any response so the client sees an IOException.
        // The StubHandler records the request before invoking the responder, so
        // requests.size() equals the number of attempts the transport actually made.
        responder = captured -> {
            throw new RuntimeException("drop connection");
        };

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");

        // (a) A keyless POST must NOT be retried — a blind retry could double-charge.
        synchronized (requests) {
            requests.clear();
        }
        assertThrows(APIConnectionException.class, () -> client.tasks().create("m", input));
        synchronized (requests) {
            assertEquals(1, requests.size()); // one attempt, no retry
        }

        // (b) A POST carrying an Idempotency-Key IS safe to retry: the server
        // collapses duplicate key+body into the first task.
        synchronized (requests) {
            requests.clear();
        }
        assertThrows(APIConnectionException.class, () -> client.tasks().create("m", input,
                CreateOptions.builder().idempotencyKey("k-1").build()));
        synchronized (requests) {
            assertEquals(3, requests.size()); // initial + maxRetries(2)
        }
    }

    @Test
    void createReplaySetsIdempotentReplay() {
        sequence(new Response(200,
                "{\"code\":200,\"message\":\"success\",\"data\":{\"taskId\":\"tk-hiapi-abc123\"}}",
                Map.of("Idempotent-Replay", "true")));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        CreatedTask created = client.tasks().create("m", input,
                CreateOptions.builder().idempotencyKey("k").build());

        assertTrue(created.isIdempotentReplay());
        assertEquals("tk-hiapi-abc123", created.getTaskId());
    }

    @Test
    void createIdempotencyMismatchNotRetried() {
        sequence(new Response(422,
                "{\"code\":422,\"error_code\":\"IDEMPOTENCY_KEY_MISMATCH\",\"message\":\"key reused\"}"));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        IdempotencyKeyMismatchException exc = assertThrows(
                IdempotencyKeyMismatchException.class,
                () -> client.tasks().create("m", input,
                        CreateOptions.builder().idempotencyKey("k").build()));

        assertEquals(422, exc.getStatus());
        synchronized (requests) {
            assertEquals(1, requests.size()); // 422 must NOT be retried
        }
    }

    @Test
    void create409ProcessingRetriesThenReplays() {
        sequence(
                new Response(409,
                        "{\"code\":409,\"error_code\":\"IDEMPOTENCY_KEY_PROCESSING\",\"message\":\"in progress\"}",
                        Map.of("Retry-After", "0")),
                new Response(200,
                        "{\"code\":200,\"message\":\"success\",\"data\":{\"taskId\":\"tk-hiapi-abc123\"}}",
                        Map.of("Idempotent-Replay", "true")));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        CreatedTask created = client.tasks().create("m", input,
                CreateOptions.builder().idempotencyKey("k").build());

        assertTrue(created.isIdempotentReplay());
        synchronized (requests) {
            assertEquals(2, requests.size()); // one automatic 409 retry
        }
    }

    @Test
    void create409ProcessingExhaustsRetriesAndThrows() {
        sequence(new Response(409,
                "{\"code\":409,\"error_code\":\"IDEMPOTENCY_KEY_PROCESSING\",\"message\":\"in progress\"}",
                Map.of("Retry-After", "0")));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        IdempotencyKeyProcessingException exc = assertThrows(
                IdempotencyKeyProcessingException.class,
                () -> client.tasks().create("m", input,
                        CreateOptions.builder().idempotencyKey("k").build()));

        assertEquals(409, exc.getStatus());
        synchronized (requests) {
            assertEquals(3, requests.size()); // initial attempt + maxRetries(2)
        }
    }

    @Test
    void create409WithoutKeyNotRetried() {
        // A 409 on a keyless POST is not the idempotency-processing contract; it
        // must surface immediately rather than being retried.
        sequence(new Response(409,
                "{\"code\":409,\"error_code\":\"IDEMPOTENCY_KEY_PROCESSING\",\"message\":\"in progress\"}",
                Map.of("Retry-After", "0")));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        assertThrows(IdempotencyKeyProcessingException.class,
                () -> client.tasks().create("m", input));

        synchronized (requests) {
            assertEquals(1, requests.size());
        }
    }

    @Test
    void runForwardsRouteAndIdempotencyKey() {
        sequence(
                ok("{\"taskId\":\"tk-hiapi-abc123\"}"),
                detail("success", "\"route\":\"ext\",\"model\":\"m@ext\""));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("prompt", "x");
        Task task = client.tasks().run("m", input, RunOptions.builder()
                .route("ext")
                .idempotencyKey("job-42")
                .pollInterval(0.01)
                .build());

        Captured createReq;
        synchronized (requests) {
            createReq = requests.get(0);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyMap = (Map<String, Object>) Json.parse(createReq.body);
        assertEquals("ext", bodyMap.get("route"));
        assertEquals("job-42", createReq.headers.get("Idempotency-Key"));
        assertEquals("ext", task.getRoute());
    }
}
