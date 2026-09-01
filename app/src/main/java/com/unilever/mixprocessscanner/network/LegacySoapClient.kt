package com.unilever.mixprocessscanner.network

import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.LogType
import com.unilever.mixprocessscanner.core.PreferencesDataStore
import com.unilever.mixprocessscanner.core.PreferencesSnapshot
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class LegacyBootResult(
    val serverTimeIso: String?,
    val setPdaStatusOk: Boolean,
    val rawGetServerTimeResponse: String,
    val rawSetStatusResponse: String
)

object LegacySoapClient {

    private const val DEFAULT_SERVER_IP = PreferencesDataStore.DEFAULT_SERVER_IP
    private const val PDA_PATH = "/PDAService/"

    private const val SOAP_ACTION_GET_SERVER_TIME =
        "http://azo-controls.com/Refill/PDAService/IPDAService/GetServerTime"

    private const val SOAP_ACTION_SET_PDA_STATUS =
        "http://azo-controls.com/Refill/PDAService/IPDAService/SetPdaStatus"

    private const val ZERO_GUID = "00000000-0000-0000-0000-000000000000"

    suspend fun runBootSequence(
        prefs: PreferencesSnapshot,
        deviceIp: String,
        batteryLevel: Int
    ): LegacyBootResult {
        val serverIp = prefs.serverIpAddress.ifBlank { DEFAULT_SERVER_IP }

        val getResp = sendGetServerTime(serverIp = serverIp)
        val serverTime = extractGetServerTimeResult(getResp)

        val setResp = sendSetPdaStatus(
            serverIp = serverIp,
            deviceId = prefs.deviceId.ifBlank { "820D28C6" },
            deviceIp = deviceIp.ifBlank { "0.0.0.0" },
            version = prefs.softwareVersion.ifBlank { "1.0.0.0" },
            batteryLevel = batteryLevel.coerceIn(0, 100)
        )

        val setOk = setResp.contains("SetPdaStatusResponse", ignoreCase = true)

        return LegacyBootResult(
            serverTimeIso = serverTime,
            setPdaStatusOk = setOk,
            rawGetServerTimeResponse = getResp,
            rawSetStatusResponse = setResp
        )
    }

    private suspend fun sendGetServerTime(serverIp: String): String {
        val endpoint = "http://$serverIp$PDA_PATH"
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <GetServerTime xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                               xmlns="http://azo-controls.com/Refill/PDAService" />
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        CommLogManager.add(LogType.HTTP_POST_OUT, "LegacySoapClient", "POST $endpoint [GetServerTime]")

        val response = KtorClientProvider.client.post(endpoint) {
            header("Content-Type", "text/xml; charset=utf-8")
            header("SOAPAction", SOAP_ACTION_GET_SERVER_TIME)
            header("Connection", "Keep-Alive")
            header("Expect", "100-continue")
            header("Host", serverIp)
            setBody(xml)
        }

        val body = response.bodyAsText()
        CommLogManager.add(
            LogType.HTTP_POST_IN,
            "LegacySoapClient",
            "GetServerTime HTTP ${response.status.value} | ${body.take(1200)}"
        )

        if (!response.status.isSuccess()) {
            throw IllegalStateException("GetServerTime failed: HTTP ${response.status.value}")
        }

        return body
    }

    private suspend fun sendSetPdaStatus(
        serverIp: String,
        deviceId: String,
        deviceIp: String,
        version: String,
        batteryLevel: Int
    ): String {
        val endpoint = "http://$serverIp$PDA_PATH"

        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <SetPdaStatus xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                              xmlns="http://azo-controls.com/Refill/PDAService">
                  <credentials>
                    <UserName></UserName>
                    <Password></Password>
                    <Token>$ZERO_GUID</Token>
                    <DeviceId>${xmlEscape(deviceId)}</DeviceId>
                    <FullUserName xsi:nil="true" />
                  </credentials>
                  <Keys>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">IPADRESS</string>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">EXECUTINGASSEMBLY</string>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">VERSION</string>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">BATTERYLEVEL</string>
                  </Keys>
                  <Values>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">${xmlEscape(deviceIp)}</string>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">AZO.Refill.PDA.Client.BLL</string>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">${xmlEscape(version)}</string>
                    <string xmlns="http://schemas.microsoft.com/2003/10/Serialization/Arrays">$batteryLevel</string>
                  </Values>
                </SetPdaStatus>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        CommLogManager.add(LogType.HTTP_POST_OUT, "LegacySoapClient", "POST $endpoint [SetPdaStatus]")

        val response = KtorClientProvider.client.post(endpoint) {
            header("Content-Type", "text/xml; charset=utf-8")
            header("SOAPAction", SOAP_ACTION_SET_PDA_STATUS)
            header("Connection", "Keep-Alive")
            header("Expect", "100-continue")
            header("Host", serverIp)
            setBody(xml)
        }

        val body = response.bodyAsText()
        CommLogManager.add(
            LogType.HTTP_POST_IN,
            "LegacySoapClient",
            "SetPdaStatus HTTP ${response.status.value} | ${body.take(1200)}"
        )

        if (!response.status.isSuccess()) {
            throw IllegalStateException("SetPdaStatus failed: HTTP ${response.status.value}")
        }

        return body
    }

    fun extractGetServerTimeResult(xml: String): String? {
        val open = "<GetServerTimeResult>"
        val close = "</GetServerTimeResult>"
        val s = xml.indexOf(open)
        val e = xml.indexOf(close)
        if (s == -1 || e == -1 || e <= s) return null
        return xml.substring(s + open.length, e).trim()
    }

    fun parseServerTimeToEpochMs(iso: String): Long? {
        return runCatching {
            OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun xmlEscape(value: String): String {
        return buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}