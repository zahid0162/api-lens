package com.apilens.core

import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.TimeUnit

/**
 * Thread-safe registry correlating OkHttp [Call] instances with [EventListener] timing sessions.
 * Uses a [WeakHashMap] so abandoned or canceled calls never leak memory.
 */
object ApiTimingRegistry {

    internal class TimingSession {
        @Volatile var dnsStartNs: Long? = null
        @Volatile var dnsEndNs: Long? = null
        @Volatile var connectStartNs: Long? = null
        @Volatile var secureConnectStartNs: Long? = null
        @Volatile var secureConnectEndNs: Long? = null
        @Volatile var connectEndNs: Long? = null
        @Volatile var requestHeadersStartNs: Long? = null
        @Volatile var requestHeadersEndNs: Long? = null
        @Volatile var requestBodyStartNs: Long? = null
        @Volatile var requestBodyEndNs: Long? = null
        @Volatile var responseHeadersStartNs: Long? = null
        @Volatile var responseHeadersEndNs: Long? = null
        @Volatile var responseBodyStartNs: Long? = null
        @Volatile var responseBodyEndNs: Long? = null
        @Volatile var callStartNs: Long? = null
        @Volatile var callEndNs: Long? = null
        @Volatile var protocol: String? = null
        @Volatile var remoteAddress: String? = null
        @Volatile var onCallFinished: ((ApiTiming) -> Unit)? = null

        fun snapshot(fallbackTotalDurationMs: Long): ApiTiming {
            val dnsMs = diffMs(dnsStartNs, dnsEndNs)
            val tlsMs = diffMs(secureConnectStartNs, secureConnectEndNs)
            val tcpMs = when {
                connectStartNs != null && secureConnectStartNs != null ->
                    diffMs(connectStartNs, secureConnectStartNs)
                connectStartNs != null && connectEndNs != null ->
                    diffMs(connectStartNs, connectEndNs)
                else -> null
            }

            val reqEndNs = requestBodyEndNs ?: requestHeadersEndNs
            val reqTransferMs = diffMs(requestHeadersStartNs, reqEndNs)
            val serverWaitMs = diffMs(reqEndNs, responseHeadersStartNs)
            val respEndNs = responseBodyEndNs ?: responseHeadersEndNs
            val respDownloadMs = diffMs(responseHeadersStartNs, respEndNs)

            val totalMs = diffMs(callStartNs, callEndNs) ?: fallbackTotalDurationMs

            return ApiTiming(
                dnsLookupMs = dnsMs,
                tcpConnectMs = tcpMs,
                tlsHandshakeMs = tlsMs,
                requestTransferMs = reqTransferMs,
                serverResponseMs = serverWaitMs,
                responseDownloadMs = respDownloadMs,
                totalDurationMs = totalMs,
                hasDetailedTiming = true,
                protocol = protocol,
                remoteAddress = remoteAddress
            )
        }

        private fun diffMs(startNs: Long?, endNs: Long?): Long? {
            if (startNs == null || endNs == null) return null
            val delta = endNs - startNs
            return if (delta >= 0L) TimeUnit.NANOSECONDS.toMillis(delta) else 0L
        }
    }

    private val sessions: MutableMap<Call, TimingSession> =
        Collections.synchronizedMap(WeakHashMap())

    internal fun getOrCreateSession(call: Call): TimingSession {
        synchronized(sessions) {
            return sessions.getOrPut(call) { TimingSession() }
        }
    }

    internal fun snapshotAndAttachCompletion(
        call: Call,
        fallbackTotalDurationMs: Long,
        onComplete: (ApiTiming) -> Unit
    ): ApiTiming? {
        val session = synchronized(sessions) { sessions[call] } ?: return null
        if (session.callEndNs != null) {
            synchronized(sessions) { sessions.remove(call) }
            return session.snapshot(fallbackTotalDurationMs)
        }
        session.onCallFinished = { finalTiming ->
            synchronized(sessions) { sessions.remove(call) }
            onComplete(finalTiming)
        }
        return session.snapshot(fallbackTotalDurationMs)
    }

    internal fun notifyFinished(call: Call) {
        val session = synchronized(sessions) { sessions[call] } ?: return
        val callback = session.onCallFinished ?: return
        callback(session.snapshot(0L))
    }
}

/**
 * OkHttp [EventListener] capturing DNS, TCP, TLS, request, server TTFB, and response timings.
 */
class ApiEventListener internal constructor() : EventListener() {

    override fun callStart(call: Call) {
        ApiTimingRegistry.getOrCreateSession(call).callStartNs = System.nanoTime()
    }

    override fun dnsStart(call: Call, domainName: String) {
        ApiTimingRegistry.getOrCreateSession(call).dnsStartNs = System.nanoTime()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        ApiTimingRegistry.getOrCreateSession(call).dnsEndNs = System.nanoTime()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        val session = ApiTimingRegistry.getOrCreateSession(call)
        session.connectStartNs = System.nanoTime()
        session.remoteAddress = inetSocketAddress.address?.hostAddress?.let { "$it:${inetSocketAddress.port}" }
    }

    override fun secureConnectStart(call: Call) {
        ApiTimingRegistry.getOrCreateSession(call).secureConnectStartNs = System.nanoTime()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        ApiTimingRegistry.getOrCreateSession(call).secureConnectEndNs = System.nanoTime()
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        val session = ApiTimingRegistry.getOrCreateSession(call)
        session.connectEndNs = System.nanoTime()
        if (protocol != null) {
            session.protocol = protocol.toString()
        }
    }

    override fun connectionAcquired(call: Call, connection: Connection) {
        val session = ApiTimingRegistry.getOrCreateSession(call)
        session.protocol = connection.protocol().toString()
        val socketAddr = connection.socket().remoteSocketAddress as? InetSocketAddress
        if (socketAddr != null) {
            session.remoteAddress = socketAddr.address?.hostAddress?.let { "$it:${socketAddr.port}" }
        }
    }

    override fun requestHeadersStart(call: Call) {
        ApiTimingRegistry.getOrCreateSession(call).requestHeadersStartNs = System.nanoTime()
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        ApiTimingRegistry.getOrCreateSession(call).requestHeadersEndNs = System.nanoTime()
    }

    override fun requestBodyStart(call: Call) {
        ApiTimingRegistry.getOrCreateSession(call).requestBodyStartNs = System.nanoTime()
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        ApiTimingRegistry.getOrCreateSession(call).requestBodyEndNs = System.nanoTime()
    }

    override fun responseHeadersStart(call: Call) {
        ApiTimingRegistry.getOrCreateSession(call).responseHeadersStartNs = System.nanoTime()
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        ApiTimingRegistry.getOrCreateSession(call).responseHeadersEndNs = System.nanoTime()
    }

    override fun responseBodyStart(call: Call) {
        ApiTimingRegistry.getOrCreateSession(call).responseBodyStartNs = System.nanoTime()
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        ApiTimingRegistry.getOrCreateSession(call).responseBodyEndNs = System.nanoTime()
    }

    override fun callEnd(call: Call) {
        ApiTimingRegistry.getOrCreateSession(call).callEndNs = System.nanoTime()
        ApiTimingRegistry.notifyFinished(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        ApiTimingRegistry.getOrCreateSession(call).callEndNs = System.nanoTime()
        ApiTimingRegistry.notifyFinished(call)
    }

    class Factory : EventListener.Factory {
        override fun create(call: Call): EventListener = ApiEventListener()
    }
}
