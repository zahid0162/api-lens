package com.apilens.core

import com.apilens.ApiLens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Executes a manual replay of a previously captured [ApiTransaction] through API Lens.
 */
internal object ApiReplayClient {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(ApiLens.interceptor())
            .eventListenerFactory(ApiLens.eventListenerFactory())
            .build()
    }

    suspend fun replay(transaction: ApiTransaction): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val req = transaction.request
            val builder = Request.Builder().url(req.url)
            req.headers.forEach { header ->
                // Skip redacted placeholder headers and content-length (OkHttp calculates it)
                if (!header.isRedacted && !header.name.equals("Content-Length", ignoreCase = true)) {
                    builder.addHeader(header.name, header.value)
                }
            }
            builder.addHeader("X-ApiLens-Replay", "true")

            val method = req.method.uppercase()
            val body = if (method in setOf("POST", "PUT", "PATCH", "DELETE") && req.body != null) {
                req.body.toRequestBody(req.contentType?.toMediaTypeOrNull())
            } else if (method in setOf("POST", "PUT", "PATCH")) {
                "".toRequestBody(req.contentType?.toMediaTypeOrNull())
            } else {
                null
            }

            builder.method(method, body)
            client.newCall(builder.build()).execute().use { response ->
                response.code
            }
        }
    }
}
