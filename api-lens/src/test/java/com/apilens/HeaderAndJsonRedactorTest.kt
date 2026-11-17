package com.apilens

import com.apilens.core.ApiHeader
import com.apilens.core.ApiRequest
import com.apilens.core.ApiResponse
import com.apilens.core.ApiTiming
import com.apilens.core.ApiTransaction
import com.apilens.utils.HeaderRedactor
import com.apilens.utils.JsonFormatter
import com.apilens.utils.RequestFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeaderAndJsonRedactorTest {

    @Test
    fun `redacts sensitive headers while preserving auth scheme`() {
        val headers = listOf(
            "Authorization" to "Bearer super-secret-jwt",
            "Cookie" to "session=xyz",
            "Content-Type" to "application/json"
        )
        val result = HeaderRedactor.redactHeaders(headers, ApiLensConfig.DEFAULT_REDACTED_HEADERS)

        assertTrue(result[0].isRedacted)
        assertTrue(result[0].value.startsWith("Bearer "))
        assertFalse(result[0].value.contains("super-secret-jwt"))

        assertTrue(result[1].isRedacted)
        assertFalse(result[1].value.contains("xyz"))

        assertFalse(result[2].isRedacted)
        assertEquals("application/json", result[2].value)
    }

    @Test
    fun `redacts sensitive query parameters in URL`() {
        val redacted = HeaderRedactor.redactUrl(
            "https://api.example.com/v1/items?page=2&api_key=secret123&token=tok99",
            ApiLensConfig.DEFAULT_REDACTED_QUERY_PARAMS
        )
        assertTrue(redacted.contains("page=2"))
        assertFalse(redacted.contains("secret123"))
        assertFalse(redacted.contains("tok99"))
        assertTrue(redacted.contains("api_key=REDACTED"))
        assertTrue(redacted.contains("token=REDACTED"))
    }

    @Test
    fun `redacts nested json secrets and passwords recursively`() {
        val raw = """
            {
              "user": {
                "email": "dev@apilens.io",
                "password": "my-password",
                "tokens": [
                  { "accessToken": "secret-access", "expiresIn": 3600 }
                ]
              }
            }
        """.trimIndent()

        val redacted = JsonFormatter.redactBody(
            raw = raw,
            contentType = "application/json",
            sensitiveKeys = ApiLensConfig.DEFAULT_REDACTED_JSON_KEYS
        )

        assertTrue(redacted.contains("dev@apilens.io"))
        assertFalse(redacted.contains("my-password"))
        assertFalse(redacted.contains("secret-access"))
        assertTrue(redacted.contains("REDACTED"))
    }

    @Test
    fun `parses json tree nodes for expandable inspection`() {
        val tree = JsonFormatter.parseJsonTree("""{"name":"API Lens","tags":["http","okhttp"],"ok":true}""")
        assertNotNull(tree)
        val root = tree as JsonFormatter.JsonTreeNode.ObjectNode
        assertEquals(3, root.entries.size)
    }

    @Test
    fun `formats cURL command and JSON export cleanly`() {
        val tx = ApiTransaction(
            id = "tx-1",
            request = ApiRequest(
                id = "tx-1",
                url = "https://api.example.com/login",
                scheme = "https",
                host = "api.example.com",
                path = "/login",
                queryParams = emptyList(),
                method = "POST",
                headers = listOf(ApiHeader("Content-Type", "application/json", false)),
                body = """{"email":"a@b.com"}""",
                contentType = "application/json",
                sizeBytes = 19L,
                timestampMs = 1_700_000_000_000L
            ),
            response = ApiResponse(
                statusCode = 200,
                message = "OK",
                headers = emptyList(),
                body = """{"ok":true}""",
                contentType = "application/json",
                sizeBytes = 11L,
                timestampMs = 1_700_000_000_120L
            ),
            timing = ApiTiming(totalDurationMs = 120L, hasDetailedTiming = false)
        )

        val curl = RequestFormatter.toCurlCommand(tx)
        assertTrue(curl.contains("curl -X POST"))
        assertTrue(curl.contains("https://api.example.com/login"))

        val exportJson = RequestFormatter.exportTransactionsToJson(listOf(tx))
        assertTrue(exportJson.contains("\"count\": 1"))
        assertTrue(exportJson.contains("\"statusCode\": 200"))
    }
}
