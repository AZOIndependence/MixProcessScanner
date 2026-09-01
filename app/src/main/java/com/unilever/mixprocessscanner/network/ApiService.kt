package com.unilever.mixprocessscanner.network

import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.GenericResponse
import com.unilever.mixprocessscanner.core.LogType
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders

class ApiService {

    suspend fun genericGet(url: String): GenericResponse {
        return try {
            CommLogManager.add(LogType.HTTP_GET_OUT, "ApiService", "GET $url")
            val response = KtorClientProvider.client.get(url).body<String>()
            CommLogManager.add(LogType.HTTP_GET_IN, "ApiService", response)
            GenericResponse(success = true, message = "GET success", payload = response)
        } catch (ex: Exception) {
            CommLogManager.addError("ApiService", "GET failed: ${ex.message}")
            GenericResponse(success = false, message = "GET failed: ${ex.message}")
        }
    }

    /**
     * Default JSON POST.
     * Fixes: "Fail to prepare request body ... LinkedHashMap ... Content-Type: null"
     */
    suspend fun genericPost(url: String, payload: Any): GenericResponse {
        return genericPostJson(url = url, payload = payload)
    }

    /**
     * Explicit JSON POST helper (header-based for broad Ktor compatibility).
     */
    suspend fun genericPostJson(url: String, payload: Any): GenericResponse {
        return try {
            CommLogManager.add(LogType.HTTP_POST_OUT, "ApiService", "POST $url | $payload")
            val response = KtorClientProvider.client.post(url) {
                header(HttpHeaders.ContentType, "application/json")
                setBody(payload)
            }.body<String>()
            CommLogManager.add(LogType.HTTP_POST_IN, "ApiService", response)
            GenericResponse(success = true, message = "POST success", payload = response)
        } catch (ex: Exception) {
            CommLogManager.addError("ApiService", "POST failed: ${ex.message}")
            GenericResponse(success = false, message = "POST failed: ${ex.message}")
        }
    }

    /**
     * Optional helper if any endpoint expects plain text body.
     */
    suspend fun genericPostText(url: String, payload: String): GenericResponse {
        return try {
            CommLogManager.add(LogType.HTTP_POST_OUT, "ApiService", "POST $url | $payload")
            val response = KtorClientProvider.client.post(url) {
                header(HttpHeaders.ContentType, "text/plain; charset=utf-8")
                setBody(payload)
            }.body<String>()
            CommLogManager.add(LogType.HTTP_POST_IN, "ApiService", response)
            GenericResponse(success = true, message = "POST success", payload = response)
        } catch (ex: Exception) {
            CommLogManager.addError("ApiService", "POST failed: ${ex.message}")
            GenericResponse(success = false, message = "POST failed: ${ex.message}")
        }
    }
}