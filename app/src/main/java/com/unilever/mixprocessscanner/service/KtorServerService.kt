package com.unilever.mixprocessscanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.unilever.mixprocessscanner.R
import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.GlobalState
import com.unilever.mixprocessscanner.core.LogType
import com.unilever.mixprocessscanner.core.TimeProvider
import com.unilever.mixprocessscanner.viewmodel.AppViewModel
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class KtorServerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var server: ApplicationEngine? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        startServer()
        startKeepAliveMonitor()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Keep service alive for enterprise device/server behavior
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            server?.stop(1_000, 2_000)
            server = null
        } catch (ex: Exception) {
            CommLogManager.addError("KtorServerService", "Error stopping server: ${ex.message}")
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startServer() {
        if (server != null) {
            CommLogManager.addInfo("KtorServerService", "startServer skipped; server already running")
            return
        }

        try {
            server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
                install(ContentNegotiation) { json() }
                install(WebSockets)

                routing {
                    get("/health") {
                        CommLogManager.add(LogType.HTTP_GET_IN, "Remote", "GET /health")
                        call.respond(HttpStatusCode.OK, "Mix Process Scanner server alive")
                    }

                    get("/status") {
                        CommLogManager.add(LogType.HTTP_GET_IN, "Remote", "GET /status")
                        val payload = mapOf(
                            "loggedIn" to GlobalState.loggedIn.value,
                            "currentUser" to GlobalState.currentUser.value,
                            "deviceIp" to GlobalState.deviceIpAddress.value
                        )
                        call.respond(HttpStatusCode.OK, payload)
                    }

                    post("/keepalive") {
                        val body = call.receiveText()
                        CommLogManager.add(
                            LogType.HTTP_POST_IN,
                            "Remote",
                            "POST /keepalive | ${body.take(600)}"
                        )

                        try {
                            val timeout = json.parseToJsonElement(body)
                                .jsonObject["timeoutSec"]
                                ?.jsonPrimitive
                                ?.content
                                ?.toIntOrNull()
                                ?.coerceIn(5, 3600)
                                ?: 60

                            val offset = AppViewModel.preferences.value.serverTimeOffsetMs
                            GlobalState.updateKeepAlive(timeout) {
                                TimeProvider.nowEpochMs(offset)
                            }
                        } catch (ex: Exception) {
                            CommLogManager.addError(
                                "KtorServerService",
                                "Invalid keepalive payload: ${ex.message}"
                            )
                        }

                        call.respond(HttpStatusCode.OK, "keepalive_received")
                    }

                    post("/scannerInstructions") {
                        val body = call.receiveText()
                        CommLogManager.add(
                            LogType.HTTP_POST_IN,
                            "Remote",
                            "POST /scannerInstructions | ${body.take(600)}"
                        )
                        GlobalState.scannerInstructions.value = body
                        call.respond(HttpStatusCode.OK, "scanner_instructions_updated")
                    }

                    post("/generic") {
                        val body = call.receiveText()
                        CommLogManager.add(
                            LogType.HTTP_POST_IN,
                            "Remote",
                            "POST /generic | ${body.take(600)}"
                        )
                        call.respond(HttpStatusCode.OK, "received")
                    }
                }
            }.start(wait = false)

            CommLogManager.addInfo("KtorServerService", "Embedded Netty server started on port 8080")
        } catch (ex: Exception) {
            server = null
            CommLogManager.addError("KtorServerService", "Failed to start server: ${ex.message}")
        }
    }

    private fun startKeepAliveMonitor() {
        serviceScope.launch {
            while (isActive) {
                try {
                    val last = GlobalState.lastKeepAliveEpochMs.value
                    val timeoutMs = (GlobalState.keepAliveTimeoutSec.value.coerceIn(5, 3600)) * 1000L

                    // Server-aligned "now"
                    val offset = AppViewModel.preferences.value.serverTimeOffsetMs
                    val now = TimeProvider.nowEpochMs(offset)

                    if (GlobalState.loggedIn.value && last > 0L && now - last > timeoutMs) {
                        GlobalState.resetLoginState()
                        CommLogManager.addInfo(
                            "KtorServerService",
                            "Auto-logout due to keep-alive timeout (${GlobalState.keepAliveTimeoutSec.value}s)"
                        )
                    }
                } catch (ex: Exception) {
                    CommLogManager.addError("KtorServerService", "KeepAlive monitor error: ${ex.message}")
                }

                delay(1_000)
            }
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.server_notification_title))
            .setContentText(getString(R.string.server_notification_text))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_CHANNEL_ID = "mix_process_scanner_service_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "Mix Process Scanner Service"
    }
}