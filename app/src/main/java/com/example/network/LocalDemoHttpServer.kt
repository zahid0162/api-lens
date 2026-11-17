package com.example.network

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight in-process HTTP/1.1 server bound to 127.0.0.1 for realistic
 * Retrofit + OkHttp debugging scenarios without relying on flaky external networks.
 */
class LocalDemoHttpServer {

    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    @Volatile
    var port: Int = 0
        private set

    val baseUrl: String
        get() = "http://127.0.0.1:$port/"

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        serverSocket = socket
        port = socket.localPort

        Thread({
            while (running.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    executor.execute { handleClient(client) }
                } catch (_: Exception) {
                    if (!running.get()) break
                }
            }
        }, "ApiLensDemoServer").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        executor.shutdownNow()
    }

    private fun handleClient(socket: Socket) {
        socket.use { client ->
            client.soTimeout = 10_000
            val reader = BufferedReader(InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val pathOnly = fullPath.substringBefore('?')

            var contentLength = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            if (contentLength > 0) {
                val buf = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(buf, read, contentLength - read)
                    if (count < 0) break
                    read += count
                }
            }

            val response = route(method, pathOnly)
            if (response.delayMs > 0L) {
                Thread.sleep(response.delayMs)
            }
            writeHttpResponse(client.getOutputStream(), response)
        }
    }

    private data class DemoResponse(
        val statusCode: Int,
        val reason: String,
        val body: String?,
        val delayMs: Long = 0L
    )

    private fun route(method: String, path: String): DemoResponse {
        return when {
            method == "GET" && path == "/api/products" -> DemoResponse(
                statusCode = 200,
                reason = "OK",
                delayMs = 45L,
                body = """
                    {
                      "catalogVersion": "2026.11",
                      "currency": "USD",
                      "items": [
                        {
                          "id": "prd_01",
                          "name": "Mechanical Tactile Keyboard",
                          "price": 149.00,
                          "inStock": true,
                          "specs": { "switches": "Tactile 62g", "wireless": true }
                        },
                        {
                          "id": "prd_02",
                          "name": "4K USB-C Reference Monitor",
                          "price": 529.50,
                          "inStock": true,
                          "specs": { "refreshHz": 120, "panel": "IPS Black" }
                        }
                      ]
                    }
                """.trimIndent()
            )

            method == "POST" && path == "/api/login" -> DemoResponse(
                statusCode = 200,
                reason = "OK",
                delayMs = 80L,
                body = """
                    {
                      "authenticated": true,
                      "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.demo_secret_token",
                      "refreshToken": "rf_live_987654321",
                      "user": {
                        "id": "usr_42",
                        "email": "developer@apilens.dev",
                        "role": "STAFF_ENGINEER"
                      }
                    }
                """.trimIndent()
            )

            method == "GET" && path == "/api/customers" -> DemoResponse(
                statusCode = 401,
                reason = "Unauthorized",
                delayMs = 35L,
                body = """
                    {
                      "error": "UNAUTHORIZED",
                      "message": "Bearer token expired or missing required scope: customers:read",
                      "retryable": false
                    }
                """.trimIndent()
            )

            method == "POST" && path == "/api/orders" -> DemoResponse(
                statusCode = 500,
                reason = "Internal Server Error",
                delayMs = 110L,
                body = """
                    {
                      "error": "ORDER_GATEWAY_FAILURE",
                      "traceId": "tr_99182_checkout",
                      "message": "Upstream inventory lock timed out while reserving SKU prd_01"
                    }
                """.trimIndent()
            )

            method == "PUT" && path == "/api/profile" -> DemoResponse(
                statusCode = 200,
                reason = "OK",
                delayMs = 55L,
                body = """
                    {
                      "updated": true,
                      "displayName": "Senior Android Engineer",
                      "notificationsEnabled": true
                    }
                """.trimIndent()
            )

            method == "PATCH" && path == "/api/preferences" -> DemoResponse(
                statusCode = 200,
                reason = "OK",
                delayMs = 40L,
                body = """
                    {
                      "theme": "obsidian-dark",
                      "compactDensity": true
                    }
                """.trimIndent()
            )

            method == "DELETE" && path == "/api/session" -> DemoResponse(
                statusCode = 204,
                reason = "No Content",
                delayMs = 25L,
                body = null
            )

            method == "GET" && path == "/api/analytics/slow" -> DemoResponse(
                statusCode = 200,
                reason = "OK",
                delayMs = 1250L,
                body = """
                    {
                      "reportId": "rep_latency_2026",
                      "p95LatencyMs": 1240,
                      "region": "europe-west2",
                      "warnings": ["Query exceeded 900ms SLA threshold"]
                    }
                """.trimIndent()
            )

            method == "GET" && path == "/api/timeout" -> DemoResponse(
                statusCode = 200,
                reason = "OK",
                delayMs = 5000L,
                body = """{"status": "too_late"}"""
            )

            else -> DemoResponse(
                statusCode = 404,
                reason = "Not Found",
                body = """{"error": "NOT_FOUND", "path": "$path"}"""
            )
        }
    }

    private fun writeHttpResponse(out: OutputStream, response: DemoResponse) {
        val bodyBytes = response.body?.toByteArray(StandardCharsets.UTF_8) ?: ByteArray(0)
        val headers = buildString {
            append("HTTP/1.1 ${response.statusCode} ${response.reason}\r\n")
            append("Server: ApiLensDemoServer/1.0\r\n")
            append("X-Request-Trace: demo-trace-01\r\n")
            append("Set-Cookie: session_id=demo_cookie_secret; HttpOnly\r\n")
            if (response.statusCode != 204) {
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ${bodyBytes.size}\r\n")
            }
            append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(StandardCharsets.UTF_8))
        if (bodyBytes.isNotEmpty() && response.statusCode != 204) {
            out.write(bodyBytes)
        }
        out.flush()
    }
}
