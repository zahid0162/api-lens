package com.apilens

import android.content.Context
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Production no-op implementation of **API Lens**.
 *
 * Exposes the exact same public API as the debug artifact while performing zero network
 * inspection, zero database persistence, zero floating overlays, and zero UI rendering.
 */
object ApiLens {

    private val passThroughInterceptor = Interceptor { chain ->
        chain.proceed(chain.request())
    }

    private val noOpEventListenerFactory = EventListener.Factory { _: Call ->
        EventListener.NONE
    }

    @JvmStatic
    @JvmOverloads
    fun initialize(context: Context, config: ApiLensConfig = ApiLensConfig()) {
        // No-op in release builds
    }

    @JvmStatic
    fun interceptor(): Interceptor = passThroughInterceptor

    @JvmStatic
    fun eventListenerFactory(): EventListener.Factory = noOpEventListenerFactory

    @JvmStatic
    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder

    @JvmStatic
    fun showDashboard(context: Context) {
        // No-op in release builds
    }

    @JvmStatic
    fun showPerformanceInspector(context: Context) {
        // No-op in release builds
    }

    @JvmStatic
    fun clearHistory() {
        // No-op in release builds
    }

    @JvmStatic
    fun disable() {
        // No-op in release builds
    }

    @JvmStatic
    fun enable() {
        // No-op in release builds
    }

    @JvmStatic
    fun isEnabled(): Boolean = false

    @JvmStatic
    fun updateConfig(config: ApiLensConfig) {
        // No-op in release builds
    }
}

fun OkHttpClient.Builder.attachApiLens(): OkHttpClient.Builder = this
