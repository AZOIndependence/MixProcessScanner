package com.unilever.mixprocessscanner.network

import com.unilever.mixprocessscanner.core.CommLogManager
import com.unilever.mixprocessscanner.core.LogType
import com.unilever.mixprocessscanner.core.TimeProvider
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.w3c.dom.Element
import java.io.IOException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random
import org.xml.sax.InputSource

class LegacyCompatibilityService(
    private val client: HttpClient,
    private val timeProvider: TimeProvider,
    private val serverOffsetMsProvider: () -> Long,
    private val serverIpProvider: () -> String,
    private val batteryLevelProvider: () -> Int = { 0 },
    private val localIpProvider: () -> String = { "" },
    private val selectedReaderProvider: () -> String = { "" },
    externalScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val TAG = "LegacyCompatService"
        private const val HEARTBEAT_MS = 30_000L
        private const val BASE_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L
        private const val MAX_RETRIES_PER_CYCLE = 5

        private const val PDA_NS = "http://azo-controls.com/Refill/PDAService"
        private const val BARCODE_NS = "http://hsh-systeme.com/Barcodeserver"
        private const val ZERO_GUID = "00000000-0000-0000-0000-000000000000"
    }

    data class Credentials(
        val token: String,
        val deviceId: String,
        val deviceIdLower: String = deviceId.lowercase()
    )

    data class ScannerEntry(
        val key: String,
        val value: String
    )

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(externalScope.coroutineContext + serviceJob + ioDispatcher)

    private val startStopMutex = Mutex()
    private val cycleMutex = Mutex()

    @Volatile
    private var loopJob: Job? = null

    private fun serverIp(): String = serverIpProvider().ifBlank { "10.162.33.168" }
    private fun pdaUrl(): String = "http://${serverIp()}/PDAService/"
    private fun barcodeUrl(): String = "http://${serverIp()}:8187/Hsh.K2.BarcodeServer.WCF/HshBarcodeService/"
    private fun nowEpochMs(): Long = timeProvider.nowEpochMs(serverOffsetMsProvider())

    private fun secureDbf(): DocumentBuilderFactory {
        val dbf = DocumentBuilderFactory.newInstance()
        dbf.isNamespaceAware = true
        runCatching { dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching { dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        dbf.isXIncludeAware = false
        dbf.isExpandEntityReferences = false
        return dbf
    }

    private fun parseBarcodeHostName(xml: String): String {
        return runCatching {
            val db = secureDbf().newDocumentBuilder()
            val doc = db.parse(InputSource(StringReader(xml)))
            val nodes = doc.getElementsByTagNameNS(
                "http://hsh-systeme.com/Barcodeserver",
                "GetHostNameResult"
            )
            if (nodes.length > 0) nodes.item(0).textContent.trim() else ""
        }.getOrElse {
            CommLogManager.add(LogType.ERROR, TAG, "parseBarcodeHostName failed: ${it.message}")
            ""
        }
    }

    fun isRunning(): Boolean = loopJob?.isActive == true

    suspend fun fetchBarcodeHostName(): String {
        val xml = postBarcodeGetHostName()
        return parseBarcodeHostName(xml)
    }

    suspend fun startHeartbeat(credentialsProvider: suspend () -> Credentials) {
        startStopMutex.withLock {
            if (loopJob?.isActive == true) {
                CommLogManager.add(LogType.INFO, TAG, "Heartbeat already running")
                return
            }

            loopJob = scope.launch {
                CommLogManager.add(LogType.INFO, TAG, "Legacy heartbeat started @${nowEpochMs()}")
                while (currentCoroutineContext().isActive) {
                    runSingleCycleWithRetry(credentialsProvider)
                    delay(HEARTBEAT_MS)
                }
            }
        }
    }

    suspend fun stopHeartbeat() {
        startStopMutex.withLock {
            loopJob?.cancel()
            loopJob = null
            CommLogManager.add(LogType.INFO, TAG, "Legacy heartbeat stopped @${nowEpochMs()}")
        }
    }

    suspend fun runSingleCycleNow(credentialsProvider: suspend () -> Credentials) {
        runSingleCycleWithRetry(credentialsProvider)
    }

    suspend fun fetchScannerNames(creds: Credentials): List<ScannerEntry> {
        postPdaGetServerTime()
        postPdaSetPdaStatus(creds)
        val scannerNamesResponse = postPdaGetScannerNames(creds)

        postBarcodeGetHostName()

        val selected = selectedReaderProvider().trim()
        if (selected.isNotBlank()) {
            postBarcodeGetReaderInfo(selected)
        }

        return parseScannerEntries(scannerNamesResponse)
    }

    suspend fun fetchReaderInfo(readerKey: String): String {
        return postBarcodeGetReaderInfo(readerKey)
    }

    suspend fun writeSimulatedBarcode(readerName: String, barcodeData: String): Boolean {
        val url = barcodeUrl()
        val soapAction = "http://hsh-systeme.com/Barcodeserver/IHshBarcodeService/Write"

        val safeReader = xmlEscape(readerName.trim())
        val safeMsg = xmlEscape("##SimulatedBarcode:${barcodeData.trim()}")

        val body = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>
            <Write xmlns="http://hsh-systeme.com/Barcodeserver"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xmlns:xsd="http://www.w3.org/2001/XMLSchema">
              <readerName>$safeReader</readerName>
              <message>$safeMsg</message>
            </Write>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

        val response = client.post(url) {
            header(HttpHeaders.ContentType, "text/xml; charset=utf-8")
            header("SOAPAction", soapAction)
            header(HttpHeaders.Connection, "Keep-Alive")
            header("Expect", "100-continue")
            setBody(body)
        }

        val xml = response.bodyAsText()
        return response.status.value in 200..299 && xml.contains("<WriteResult>true</WriteResult>")
    }

    private suspend fun runSingleCycleWithRetry(credentialsProvider: suspend () -> Credentials) {
        if (!cycleMutex.tryLock()) {
            CommLogManager.add(LogType.INFO, TAG, "Cycle skipped (already in progress)")
            return
        }

        try {
            var attempt = 0
            var lastError: Throwable? = null

            while (attempt < MAX_RETRIES_PER_CYCLE && currentCoroutineContext().isActive) {
                try {
                    val creds = credentialsProvider()
                    val start = nowEpochMs()

                    postPdaGetServerTime()
                    postPdaSetPdaStatus(creds)
                    postPdaGetScannerNames(creds)

                    postBarcodeGetHostName()

                    val selected = selectedReaderProvider().trim()
                    if (selected.isNotBlank()) {
                        postBarcodeGetReaderInfo(selected)
                    }

                    val end = nowEpochMs()
                    CommLogManager.add(LogType.INFO, TAG, "Cycle OK in ${end - start} ms @$end")
                    return
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    lastError = t
                    attempt++
                    val backoff = computeBackoffWithJitter(attempt)
                    CommLogManager.add(
                        LogType.ERROR,
                        TAG,
                        "Cycle attempt $attempt failed: ${t.message}; retry in ${backoff}ms"
                    )
                    delay(backoff)
                }
            }

            CommLogManager.add(LogType.ERROR, TAG, "Cycle failed after retries: ${lastError?.message}")
        } finally {
            cycleMutex.unlock()
        }
    }

    private fun computeBackoffWithJitter(attempt: Int): Long {
        val exp = BASE_BACKOFF_MS * 2.0.pow((attempt - 1).toDouble())
        val capped = min(exp.toLong(), MAX_BACKOFF_MS)
        return capped + Random.nextLong(0, 500)
    }

    // ---------------- PDA ----------------

    private suspend fun postPdaGetServerTime(): String {
        val action = "$PDA_NS/IPDAService/GetServerTime"
        val envelope = soapEnvelopeAzo("GetServerTime", "")
        return postSoap(pdaUrl(), action, envelope, "PDA.GetServerTime")
    }

    private suspend fun postPdaSetPdaStatus(creds: Credentials): String {
        val action = "$PDA_NS/IPDAService/SetPdaStatus"

        val token = creds.token.ifBlank { ZERO_GUID }
        val ipPayload = localIpProvider().ifBlank { "0.0.0.0" }
        val battery = batteryLevelProvider().coerceIn(0, 100)

        val envelope = soapEnvelopeAzo(
            method = "SetPdaStatus",
            bodyInnerXml = """
                <credentials>
                  <UserName></UserName>
                  <Password></Password>
                  <Token>${xmlEscape(token)}</Token>
                  <DeviceId>${xmlEscape(creds.deviceId)}</DeviceId>
                  <FullUserName xsi:nil="true"/>
                </credentials>
                <Keys xmlns:a="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
                  <a:string>IPADRESS</a:string>
                  <a:string>EXECUTINGASSEMBLY</a:string>
                  <a:string>VERSION</a:string>
                  <a:string>BATTERYLEVEL</a:string>
                </Keys>
                <Values xmlns:a="http://schemas.microsoft.com/2003/10/Serialization/Arrays">
                  <a:string>${xmlEscape(ipPayload)}</a:string>
                  <a:string>AZO.Refill.PDA.Client.BLL</a:string>
                  <a:string>1.0.16265.0</a:string>
                  <a:string>$battery</a:string>
                </Values>
            """.trimIndent()
        )
        return postSoap(pdaUrl(), action, envelope, "PDA.SetPdaStatus")
    }

    private suspend fun postPdaGetScannerNames(creds: Credentials): String {
        val action = "$PDA_NS/IPDAService/GetScannerNames"
        val token = creds.token.ifBlank { ZERO_GUID }

        val envelope = soapEnvelopeAzo(
            method = "GetScannerNames",
            bodyInnerXml = """
                <credentials>
                  <UserName></UserName>
                  <Password></Password>
                  <Token>${xmlEscape(token)}</Token>
                  <DeviceId>${xmlEscape(creds.deviceId)}</DeviceId>
                  <FullUserName xsi:nil="true"/>
                </credentials>
                <deviceId>${xmlEscape(creds.deviceId)}</deviceId>
            """.trimIndent()
        )
        return postSoap(pdaUrl(), action, envelope, "PDA.GetScannerNames")
    }

    // ---------------- Barcode ----------------

    private suspend fun postBarcodeGetHostName(): String {
        val action = "$BARCODE_NS/IHshBarcodeService/GetHostName"
        val envelope = soapEnvelopeBarcode("GetHostName", "")
        return postSoap(barcodeUrl(), action, envelope, "BARCODE.GetHostName")
    }

    private suspend fun postBarcodeGetReaderInfo(readerKey: String): String {
        val action = "$BARCODE_NS/IHshBarcodeService/GetReaderInfo"
        val envelope = soapEnvelopeBarcode(
            method = "GetReaderInfo",
            bodyInnerXml = "<readerName>${xmlEscape(readerKey)}</readerName>"
        )
        return postSoap(barcodeUrl(), action, envelope, "BARCODE.GetReaderInfo")
    }

    // ---------------- Core SOAP ----------------

    private suspend fun postSoap(url: String, soapAction: String, envelope: String, callName: String): String {
        val tsStart = nowEpochMs()
        CommLogManager.add(LogType.INFO, TAG, "[$callName] -> POST $url @$tsStart")

        val response = client.post(url) {
            contentType(ContentType.Text.Xml)
            header("SOAPAction", soapAction) // unquoted: legacy-compatible
            header("Connection", "Keep-Alive")
            header("Expect", "100-continue")
            setBody(envelope)
        }

        val body = response.body<String>()
        val tsEnd = nowEpochMs()

        CommLogManager.add(
            LogType.INFO,
            TAG,
            "[$callName] <- HTTP ${response.status.value} in ${tsEnd - tsStart}ms @$tsEnd"
        )
        CommLogManager.add(LogType.INFO, TAG, "[$callName] REQ: ${envelope.take(1200)}")
        CommLogManager.add(LogType.INFO, TAG, "[$callName] RES: ${body.take(1200)}")

        if (response.status.value !in 200..299) {
            throw IOException("[$callName] HTTP ${response.status.value}")
        }
        return body
    }

    // ---------------- XML parse ----------------

    private fun parseScannerEntries(xml: String): List<ScannerEntry> {
        return runCatching {
            val db = secureDbf().newDocumentBuilder()
            val doc = db.parse(InputSource(StringReader(xml)))

            val nodes = doc.getElementsByTagNameNS(
                "http://schemas.microsoft.com/2003/10/Serialization/Arrays",
                "KeyValueOfstringstring"
            )

            val out = mutableListOf<ScannerEntry>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i) as? Element ?: continue
                val key = node.getElementsByTagNameNS(
                    "http://schemas.microsoft.com/2003/10/Serialization/Arrays",
                    "Key"
                ).item(0)?.textContent?.trim().orEmpty()
                val value = node.getElementsByTagNameNS(
                    "http://schemas.microsoft.com/2003/10/Serialization/Arrays",
                    "Value"
                ).item(0)?.textContent?.trim().orEmpty()

                if (key.isNotBlank() && value.isNotBlank()) {
                    out += ScannerEntry(key = key, value = value)
                }
            }
            out
        }.getOrElse {
            CommLogManager.add(LogType.ERROR, TAG, "parseScannerEntries failed: ${it.message}")
            emptyList()
        }
    }

    // ---------------- envelopes ----------------

    private fun soapEnvelopeAzo(method: String, bodyInnerXml: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>
            <$method xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                     xmlns="$PDA_NS">
              $bodyInnerXml
            </$method>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

    private fun soapEnvelopeBarcode(method: String, bodyInnerXml: String): String = """
        <?xml version="1.0" encoding="utf-8"?>
        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
          <s:Body>
            <$method xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:xsd="http://www.w3.org/2001/XMLSchema"
                     xmlns="$BARCODE_NS">
              $bodyInnerXml
            </$method>
          </s:Body>
        </s:Envelope>
    """.trimIndent()

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

    fun shutdown() {
        serviceJob.cancel()
    }
}