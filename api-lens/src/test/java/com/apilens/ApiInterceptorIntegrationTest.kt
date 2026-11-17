package com.apilens

import com.apilens.core.ApiErrorType
import com.apilens.core.ApiEventListener
import com.apilens.core.ApiInterceptor
import com.apilens.data.ApiRepository
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ApiInterceptorIntegrationTest {

    private lateinit var serverSocket: ServerSocket
    private var port: Int = 0

    @Volatile
    private var responseDelayMs: Long = 0L

    @Volatile
    private var responsePayload: String = """{"status":"ok","accessToken":"secret_tok_123"}"""

    @Before
    fun setUp() {
        serverSocket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
        port = serverSocket.localPort
        Thread {
            while (!serverSocket.isClosed) {
                try {
                    val client = serverSocket.accept()
                    Thread {
                        client.use { socket ->
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            var contentLen = 0
                            while (true) {
                                val line = reader.readLine() ?: break
                                if (line.isEmpty()) break
                                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                                    contentLen = line.substringAfter(':').trim().toIntOrNull() ?: 0
                                }
                            }
                            if (contentLen > 0) {
                                val buf = CharArray(contentLen)
                                reader.read(buf, 0, contentLen)
                            }
                            if (responseDelayMs > 0L) {
                                Thread.sleep(responseDelayMs)
                            }
                            val bodyBytes = responsePayload.toByteArray(StandardCharsets.UTF_8)
                            val resp = buildString {
                                append("HTTP/1.1 200 OK\r\n")
                                append("Content-Type: application/json\r\n")
                                append("Content-Length: ${bodyBytes.size}\r\n")
                                append("Connection: close\r\n\r\n")
                            }
                            socket.getOutputStream().write(resp.toByteArray(StandardCharsets.UTF_8))
                            socket.getOutputStream().write(bodyBytes)
                            socket.getOutputStream().flush()
                        }
                    }.start()
                } catch (_: Exception) {
                    break
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    @After
    fun tearDown() {
        runCatching { serverSocket.close() }
    }

    @Test
    fun `captures request and response without corrupting caller stream and redacts secrets`() {
        var config = ApiLensConfig(enableDatabase = false, maxBodySizeBytes = 256 * 1024L)
        val repo = ApiRepository(context = null, initialConfig = config, ioDispatcher = Dispatchers.Unconfined)
        val interceptor = ApiInterceptor(
            configProvider = { config },
            isEnabledProvider = { true },
            repositoryProvider = { repo }
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .eventListenerFactory(ApiEventListener.Factory())
            .build()

        val request = Request.Builder()
            .url("http://127.0.0.1:$port/api/login")
            .header("Authorization", "Bearer top-secret-bearer")
            .post("""{"email":"a@b.com","password":"rawPassword99"}""".toRequestBody("application/json".toMediaType()))
            .build()

        val callerBody = client.newCall(request).execute().use { resp ->
            assertEquals(200, resp.code)
            resp.body?.string()
        }

        // Caller must receive unmodified full response body
        assertEquals(responsePayload, callerBody)

        val captured = repo.transactions.value.firstOrNull()
        assertNotNull(captured)
        captured!!
        assertFalse(captured.isPending)
        assertTrue(captured.isSuccess)
        assertTrue(captured.timing.hasDetailedTiming)

        // Secrets redacted in captured trace
        val authHeader = captured.request.headers.first { it.name == "Authorization" }
        assertTrue(authHeader.isRedacted)
        assertFalse(authHeader.value.contains("top-secret-bearer"))
        assertFalse(captured.request.body.orEmpty().contains("rawPassword99"))
        assertFalse(captured.response?.body.orEmpty().contains("secret_tok_123"))
    }

    @Test
    fun `truncates oversized captured body while preserving full body for caller`() {
        val largePayload = """{"data":"${"x".repeat(500)}"}"""
        responsePayload = largePayload

        val config = ApiLensConfig(enableDatabase = false, maxBodySizeBytes = 64L)
        val repo = ApiRepository(context = null, initialConfig = config, ioDispatcher = Dispatchers.Unconfined)
        val interceptor = ApiInterceptor(
            configProvider = { config },
            isEnabledProvider = { true },
            repositoryProvider = { repo }
        )

        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        val callerBody = client.newCall(
            Request.Builder().url("http://127.0.0.1:$port/large").build()
        ).execute().use { it.body?.string() }

        assertEquals(largePayload, callerBody)

        val captured = repo.transactions.value.first()
        assertTrue(captured.response!!.bodyTruncated)
        assertTrue((captured.response?.body?.length ?: 0) <= 64)
        // Without EventListener attached, detailed timings are explicitly null (not 0ms)
        assertFalse(captured.timing.hasDetailedTiming)
        assertNull(captured.timing.dnsLookupMs)
        assertNull(captured.timing.tcpConnectMs)
    }

    @Test
    fun `classifies socket timeout and rethrows IOException`() {
        responseDelayMs = 1500L
        val config = ApiLensConfig(enableDatabase = false)
        val repo = ApiRepository(context = null, initialConfig = config, ioDispatcher = Dispatchers.Unconfined)
        val interceptor = ApiInterceptor(
            configProvider = { config },
            isEnabledProvider = { true },
            repositoryProvider = { repo }
        )

        val client = OkHttpClient.Builder()
            .readTimeout(200, TimeUnit.MILLISECONDS)
            .addInterceptor(interceptor)
            .build()

        var thrown = false
        try {
            client.newCall(Request.Builder().url("http://127.0.0.1:$port/slow").build()).execute()
        } catch (_: IOException) {
            thrown = true
        }
        assertTrue(thrown)

        val captured = repo.transactions.value.first()
        assertTrue(captured.isFailure)
        assertEquals(ApiErrorType.SOCKET_TIMEOUT, captured.errorType)
    }

    @Test
    fun `respects maxRequests history cap`() {
        val config = ApiLensConfig(enableDatabase = false, maxRequests = 3)
        val repo = ApiRepository(context = null, initialConfig = config, ioDispatcher = Dispatchers.Unconfined)
        val interceptor = ApiInterceptor(
            configProvider = { config },
            isEnabledProvider = { true },
            repositoryProvider = { repo }
        )

        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()
        repeat(5) { idx ->
            client.newCall(Request.Builder().url("http://127.0.0.1:$port/item/$idx").build()).execute().close()
        }

        assertEquals(3, repo.transactions.value.size)
    }
}
