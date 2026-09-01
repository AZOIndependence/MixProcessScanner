package com.unilever.mixprocessscanner.network

import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.LogType
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

object KtorClientProvider {

    private const val TAG = "KtorClientProvider"

    val client: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    // Keep-alive / pooling behavior for SOAP-heavy traffic
                    connectionPool(ConnectionPool(20, 5, TimeUnit.MINUTES))
                    retryOnConnectionFailure(true)

                    // Conservative transport timeouts
                    connectTimeout(15, TimeUnit.SECONDS)
                    readTimeout(30, TimeUnit.SECONDS)
                    writeTimeout(30, TimeUnit.SECONDS)
                    callTimeout(45, TimeUnit.SECONDS)
                }
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 45_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }

            install(HttpRequestRetry) {
                maxRetries = 2
                retryOnServerErrors(maxRetries)
                retryIf { _, response ->
                    // Retry transient server-side errors
                    response.status.value in 500..599
                }
                retryOnExceptionIf { _, cause ->
                    // Retry IO/transient connectivity failures
                    cause is java.io.IOException
                }
                exponentialDelay(base = 500.0, maxDelayMs = 3_000)
            }

            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = false
                        isLenient = true
                    }
                )
            }

            install(WebSockets)

            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        CommLogManager.add(LogType.INFO, TAG, message.take(1200))
                    }
                }
                // BODY can leak payloads; keep HEADERS/INFO for enterprise safety
                level = LogLevel.HEADERS
            }

            expectSuccess = false
        }.also {
            CommLogManager.add(LogType.INFO, TAG, "Ktor client initialized")
        }
    }
}