[![](https://jitpack.io/v/zahid0162/api-lens.svg)](https://jitpack.io/#zahid0162/api-lens)

[![](https://jitpack.io/v/zahid0162/api-lens.svg)](https://jitpack.io/#zahid0162/api-lens)

# API Lens — In-App HTTP Debugging Library for Android

**API Lens** is an open-source, embeddable Android library built with **Kotlin, Jetpack Compose, Material 3, Room, and OkHttp**. It provides a real-time in-app API debugging dashboard and draggable floating counter overlay for inspecting HTTP requests, responses, headers, JSON trees, errors, and socket timings directly inside your debug builds—with zero `SYSTEM_ALERT_WINDOW` permissions and a zero-footprint `api-lens-noop` artifact for release builds.

## Application Preview

![Desktop Preview](https://github.com/user-attachments/assets/3b3d688d-139b-4001-a9c3-562a6f9add41)

### Mobile Screenshots

![Screenshot 1](https://github.com/user-attachments/assets/c4bfa152-8020-47aa-949b-05cd4a462b58)

![Screenshot 2](https://github.com/user-attachments/assets/6a7bdbcc-9196-4a17-b6ab-348e8ced6bac)

![Screenshot 3](https://github.com/user-attachments/assets/3d856ff9-c31c-464b-a7c2-97b40a6f34ac)

---

## Key Features

- **OkHttp & Retrofit Ready**: Works seamlessly with single or multiple Retrofit services and direct `OkHttpClient` instances.
- **Safe Non-Consuming Capture**: Uses bounded `response.peekBody(...)` and non-mutating request sink forwarding so host streams, large payloads, and one-shot/duplex bodies are never corrupted.
- **Detailed Network Timing (`EventListener`)**: Captures DNS lookup, TCP connect, TLS handshake, request transfer, server TTFB, and response download durations—explicitly distinguishing `Unavailable` phases (pooled connections or missing `EventListener`) from `0 ms`.
- **Interactive Compose Dashboard**:
  - Live KPI strip (Total, Success, Failed, Avg Latency) via `StateFlow`.
  - Filter by All / Success / Failed / Slow / HTTP Method, search URLs/paths/statuses, and sort by Latest / Oldest / Slowest / Fastest.
  - Adaptive List-Detail layout on tablets/foldables and edge-to-edge phone navigation.
- **Deep Request Inspection**:
  - **Overview**: Full URL, status, error classification (`SocketTimeout`, `UnknownHost`, `SSL`, etc.), timestamps, sizes, protocol, remote address.
  - **Request**: Query params, headers, form-urlencoded key/values, formatted/raw body.
  - **Response**: Headers, collapsible **JSON Tree**, syntax-highlighted **Pretty JSON**, **Raw**, in-body search with hit count, and Copy JSON / Copy Raw.
  - **Timing**: Waterfall breakdown chart.
- **Performance Inspector**: Live app UI **FPS & Frame Jank** monitor (`Choreographer`), **JVM / Native Heap & Thread** telemetry with manual GC trigger, **P50/P90/P95 Latency Percentiles**, SLA bar chart, and tappable **Endpoint Bottleneck Leaderboard**.
- **Developer Tools**: Copy as `cURL`, Share trace, JSON history export, Slow API detection, Side-by-side Response Comparison, and Request Replay with security warning.
- **Security First**: Automatic redaction of `Authorization`, `Cookie`, `Set-Cookie`, custom headers, sensitive query params, and nested JSON keys (`password`, `token`, `accessToken`, `secret`, etc.) before persistence. All data stays 100% local.
- **Zero-Permission Floating Overlay**: Lifecycle-aware `WRAP_CONTENT` `ComposeView` attached across multiple Activities without `SYSTEM_ALERT_WINDOW` or Activity memory leaks.

---

## Gradle Integration (JitPack)

### 1. Add JitPack repository

In `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Debug & Release Dependencies

Always use `debugImplementation` for `api-lens` and `releaseImplementation` for `api-lens-noop` so release APKs contain zero debugging UI, storage, or inspection code:

```kotlin
dependencies {
    debugImplementation("com.github.zahid0162.api-lens:api-lens:v1.0.3")
    releaseImplementation("com.github.zahid0162.api-lens:api-lens-noop:v1.0.3")
}
```

---

## Quick Start

### 1. Initialize in `Application.onCreate()` (Kotlin)

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ApiLens.initialize(
            context = applicationContext,
            config = ApiLensConfig(
                showFloatingButton = true,
                floatingButtonPosition = FloatingButtonPosition.BOTTOM_END,
                maxRequests = 500,
                enableDatabase = true,
                enableBodyInspection = true,
                enablePerformanceInspector = true,
                slowRequestThresholdMs = 1000L,
                theme = ApiLensTheme.DARK
            )
        )
    }
}
```

### 2. Attach to OkHttp & Retrofit (Kotlin)

```kotlin
// Recommended: attaches both Interceptor and EventListener for full timing waterfall
val okHttpClient = OkHttpClient.Builder()
    .attachApiLens()
    .build()

// Or manually:
val okHttpClientManual = OkHttpClient.Builder()
    .addInterceptor(ApiLens.interceptor())
    .eventListenerFactory(ApiLens.eventListenerFactory())
    .build()

val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .client(okHttpClient)
    .addConverterFactory(MoshiConverterFactory.create())
    .build()
```

### 3. Java Integration Example

```java
public class MyJavaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ApiLensConfig config = new ApiLensConfig.Builder()
                .showFloatingButton(true)
                .maxRequests(500)
                .enableDatabase(true)
                .enableBodyInspection(true)
                .theme(ApiLensTheme.DARK)
                .build();

        ApiLens.initialize(getApplicationContext(), config);
    }

    public OkHttpClient createClient() {
        return ApiLens.configure(new OkHttpClient.Builder()).build();
    }
}
```

### 4. Runtime Controls

```kotlin
ApiLens.showDashboard(activity)             // Open Traffic dashboard programmatically
ApiLens.showPerformanceInspector(activity)  // Open Performance Inspector directly
ApiLens.clearHistory()                      // Clear stored traces
ApiLens.disable()                           // Pause interception, perf tracking & overlay
ApiLens.enable()                            // Resume interception, perf tracking & overlay
```

---

## Project Modules

| Module | Purpose |
| --- | --- |
| `:api-lens` | Full debug library (OkHttp Interceptor + EventListener, Room DB, Compose Dashboard, Details, Overlay, Redactor) |
| `:api-lens-noop` | Release no-op library with identical public API and zero UI/Room/inspection overhead |
| `:app` | Interactive sample application with multiple Retrofit services, embedded loopback test server, error scenarios, and multi-Activity overlay demo |
