package com.example

import android.app.Application
import com.apilens.ApiLens
import com.apilens.ApiLensConfig
import com.apilens.ApiLensTheme
import com.apilens.FloatingButtonPosition
import com.example.network.LocalDemoHttpServer

class SampleApplication : Application() {

    lateinit var demoServer: LocalDemoHttpServer
        private set

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
                slowRequestThresholdMs = 900L,
                theme = ApiLensTheme.DARK
            )
        )

        demoServer = LocalDemoHttpServer().apply { start() }
    }

    override fun onTerminate() {
        demoServer.stop()
        super.onTerminate()
    }
}
