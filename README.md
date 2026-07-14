# HiAPI Java SDK

Zero-dependency Java client for the [HiAPI](https://hiapi.ai) **unified async task API** (`/v1/tasks`) — submit an image / video / audio generation task, poll it to completion, and read the output in **one call**.

- **Zero runtime dependencies.** JDK only (`java.net.http.HttpClient`, `javax.crypto.Mac`, a hand-rolled JSON parser).
- **One-call workflow.** `client.tasks().run(...)` submits and waits for you.
- **Immutable models.** Plain `final` fields + getters, parsed straight off the wire.
- **Webhook verification.** HMAC-SHA256 signature + timestamp freshness check, built in (still deduplicate deliveries by task id).

> For OpenAI-compatible chat/image endpoints, keep using your existing OpenAI Java
> client with `baseUrl = "https://api.hiapi.ai/v1"`. This SDK focuses on what the
> OpenAI client can't do: the asynchronous **submit → poll → download** lifecycle.

## Requirements

- **Java 11+** (built against `maven.compiler.release=11`).

## Install

Maven coordinates:

```xml
<dependency>
  <groupId>ai.hiapi</groupId>
  <artifactId>hiapi</artifactId>
  <version>0.2.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'ai.hiapi:hiapi:0.2.0'
```

## Quick start

```java
import ai.hiapi.HiAPI;
import ai.hiapi.Task;
import ai.hiapi.Output;
import ai.hiapi.RunOptions;
import java.util.Map;

public class Quickstart {
    public static void main(String[] args) {
        HiAPI client = new HiAPI("sk-...");  // or set HIAPI_API_KEY

        Task task = client.tasks().run(
            "seedance-2-0",
            Map.of(
                "prompt", "a cyan glass data center entrance",
                "resolution", "1080p"
            ),
            RunOptions.builder()
                .onUpdate(t -> System.out.println("status: " + t.getStatus()))
                .build()
        );

        for (Output out : task.getOutput()) {
            System.out.println(out.getType() + " " + out.getUrl());
            // e.g. "video https://cdn.hiapi.ai/tasks/..."
        }
    }
}
```

`run()` blocks until the task reaches a terminal state. It throws `TaskFailedException`
if the task fails and `PollTimeoutException` if it doesn't finish within the timeout
(default 600s).

## Lower-level control

When you want to drive the lifecycle yourself, use `create` / `retrieve` / `list` /
`waitFor` directly:

```java
import ai.hiapi.CreatedTask;
import ai.hiapi.Task;
import ai.hiapi.TaskPage;
import java.util.Map;

CreatedTask created = client.tasks().create(
    "seedance-2-0",
    Map.of("prompt", "...", "resolution", "720p"),
    Map.of("url", "https://your-app.com/hiapi/callback", "when", "final")
);
System.out.println(created.getTaskId());

Task one = client.tasks().retrieve(created.getTaskId());   // one status check

// poll until terminal: pollIntervalSeconds, timeoutSeconds, onUpdate (may be null)
Task done = client.tasks().waitFor(
    created.getTaskId(), 3.0, 900.0,
    t -> System.out.println("status: " + t.getStatus())
);

TaskPage page = client.tasks().list(1, 20);                // newest first
for (Task t : page.getItems()) {
    System.out.println(t.getTaskId() + " " + t.getStatus());
}
```

`input` fields are **defined per model** — see the relevant
[model page](https://docs.hiapi.ai/models/). Don't put callback fields inside
`input`; pass `callback` separately.

You can also tune `run()` with `RunOptions`:

```java
import ai.hiapi.RunOptions;
import ai.hiapi.Task;
import java.util.Map;

RunOptions opts = RunOptions.builder()
    .callback(Map.of("url", "https://your-app.com/hiapi/callback", "when", "final"))
    .pollInterval(3.0)
    .timeout(900.0)
    .onUpdate(t -> System.out.println("status: " + t.getStatus()))
    .build();

Task task = client.tasks().run("seedance-2-0", Map.of("prompt", "..."), opts);
```

## Model routes

Some models expose multiple routes (e.g. `ext`) with different pricing or
upstream capacity. Pass `route` via `CreateOptions` instead of writing the
`model@route` suffix:

```java
import ai.hiapi.CreateOptions;

CreatedTask created = client.tasks().create(
    "gpt-image-2/text-to-image",
    Map.of("prompt", "..."),
    CreateOptions.builder().route("ext").build()   // preferred over "...@ext"
);
```

Omitting `route` (or passing `"default"`) uses the model's default route. An
unknown route fails fast with a 400 whose message lists the available routes.
The legacy `"x@ext"` spelling keeps working. When a task was submitted with
`route`, its detail echoes `task.getRoute()` and `task.getModel()` holds the
resolved full name (`x@ext`).

## Idempotent retries

Set `idempotencyKey` (sent as the `Idempotency-Key` header, ≤255 bytes) to make
task submission safe to retry — retrying the same key + same body within about
24 hours returns the first task instead of creating and billing a new one
(after that the key is cleaned up and the same request creates a new task):

```java
CreatedTask created = client.tasks().create(
    "seedance-2-0",
    Map.of("prompt", "..."),
    CreateOptions.builder().idempotencyKey("order-8472:video").build()
);
if (created.isIdempotentReplay()) {
    System.out.println("hit the idempotency cache; no new task created");
}
```

With a key set, the SDK also retries the POST on network errors and
retries `409 IDEMPOTENCY_KEY_PROCESSING` (the first request is
still in flight) up to the retry limit (default 2), honouring `Retry-After` capped at 60s. Reusing a key with a **different** body throws
`IdempotencyKeyMismatchException` — that's a key-construction bug, not
retryable. Both options are also available on `RunOptions` for `run()`.

## Webhooks

If you set a **Webhook signing key** in the HiAPI console, terminal callbacks are
signed. You must pass that **same key** to the client (via the builder), then verify
each request against its **raw body bytes**:

```java
import ai.hiapi.HiAPI;
import ai.hiapi.Task;
import ai.hiapi.WebhookVerificationException;
import java.time.Duration;
import java.util.Map;

// Sketch — wire into your HTTP framework of choice. readRawRequestBody(),
// readRequestHeaders() and respond() stand in for your framework's
// request/response API; cap the request body size (e.g. 1 MiB) before reading.
HiAPI client = HiAPI.builder()
    .apiKey("sk-...")
    .webhookSecret("whsec_...")   // SAME key you set in the HiAPI console
    .build();

// In your HTTP handler — pass the raw body bytes and the request headers:
byte[] rawBody = readRawRequestBody();          // do NOT re-serialize
Map<String, String> headers = readRequestHeaders();

try {
    Task task = client.webhooks().verify(rawBody, headers);
    if (task.isSucceeded() && !task.getOutput().isEmpty()) {
        System.out.println(task.getOutput().get(0).getUrl());
    }
    respond(200, "");           // ack with 2xx; HiAPI retries non-2xx
} catch (WebhookVerificationException e) {
    respond(400, "");           // bad signature or stale timestamp
}
```

The client reads the `X-HiAPI-Timestamp` and `X-HiAPI-Signature` headers
(case-insensitive), recomputes `HMAC_SHA256(secret, timestamp + "." + rawBody)`,
compares it in constant time, and rejects timestamps outside a 300-second window.

Callbacks are delivered **at least once** — deduplicate by `task.getTaskId()`.

## Errors

All exceptions are unchecked (`RuntimeException` subtypes) in package `ai.hiapi`.

| Exception | When |
| --- | --- |
| `AuthenticationException` | 401 — bad/missing API key |
| `NotFoundException` | 404 — unknown task or not yours |
| `InvalidRequestException` | `INVALID_REQUEST` — fix the request |
| `ModelUnavailableException` | `MODEL_UNAVAILABLE` — retry or switch model |
| `TaskTimeoutException` / `StorageUnavailableException` | retryable upstream errors |
| `ServiceUnavailableException` | 503 — platform busy (auto-retried) |
| `IdempotencyKeyProcessingException` | 409 — same key still in flight (auto-retried; retryable) |
| `IdempotencyKeyMismatchException` | 422 — key reused with a different body (**not** retryable) |
| `APIConnectionException` | network failure (auto-retried for reads only — **not** a keyless `create()`) |
| `APIException` | other non-2xx responses (carries `getStatus()`, `getErrorCode()`, `getBody()`) |
| `TaskFailedException` | a polled task ended in `status=fail` |
| `PollTimeoutException` | `run()` / `waitFor()` exceeded its timeout |
| `WebhookVerificationException` | bad signature or stale timestamp |

`429`/`503` are retried automatically with exponential backoff (`maxRetries`, default 2;
honours `Retry-After`). Network errors are retried **only for idempotent reads**
(`retrieve` / `list`, and the polling inside `waitFor` / `run`). The `POST` that `create()`
issues is **never** retried on a network failure — unless you set `idempotencyKey`,
which makes the retry safe server-side. Without a key, a dropped connection can't
silently create a second, double-charged task — if `create()` throws
`APIConnectionException`, confirm with `list()` before retrying.

## Configuration

Use the constructor for defaults, or `HiAPI.builder()` to override:

```java
import ai.hiapi.HiAPI;
import java.time.Duration;

HiAPI client = HiAPI.builder()
    .apiKey("sk-...")                          // falls back to HIAPI_API_KEY
    .baseUrl("https://api.hiapi.ai/v1")        // default
    .timeout(Duration.ofSeconds(60))           // per-request, default 60s
    .maxRetries(2)                             // default 2
    .webhookSecret("whsec_...")                // falls back to HIAPI_WEBHOOK_SECRET
    .build();
```

`new HiAPI("sk-...")` is shorthand for the defaults above. Passing `null` for the API
key (or omitting `apiKey` on the builder) falls back to the `HIAPI_API_KEY` environment
variable; if neither is set, the client throws `HiAPIException`.

## License

MIT
