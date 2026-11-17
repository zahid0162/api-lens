package com.apilens

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ApiLensNoOpTest {

    @Test
    fun `noop artifact exposes matching API and stays disabled`() {
        val config = ApiLensConfig.Builder()
            .showFloatingButton(true)
            .maxRequests(250)
            .theme(ApiLensTheme.LIGHT)
            .build()

        assertEquals(250, config.maxRequests)
        assertEquals(ApiLensTheme.LIGHT, config.theme)

        ApiLens.enable()
        assertFalse(ApiLens.isEnabled())

        val client = OkHttpClient.Builder()
            .attachApiLens()
            .addInterceptor(ApiLens.interceptor())
            .eventListenerFactory(ApiLens.eventListenerFactory())
            .build()

        assertNotNull(client)
        ApiLens.clearHistory()
        ApiLens.disable()
    }
}
