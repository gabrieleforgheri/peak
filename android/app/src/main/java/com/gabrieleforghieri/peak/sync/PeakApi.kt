package com.gabrieleforghieri.peak.sync

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Un documento come viaggia fra telefono e server: `kind` e `uuid` lo identificano, `data` è il contenuto (`null` per una
 * cancellazione), `m` l'istante dell'ultima modifica in millisecondi. Come `WireDoc` in PeakAPI.swift.
 */
data class WireDoc(val kind: String, val uuid: String, val data: JsonObject?, val m: Long, val deleted: Boolean) {
    val key: String get() = "$kind:$uuid"

    fun toJson(): JsonObject = JsonObject(
        mapOf(
            "kind" to JsonPrimitive(kind), "uuid" to JsonPrimitive(uuid), "data" to (data ?: JsonNull),
            "m" to JsonPrimitive(m), "deleted" to JsonPrimitive(deleted),
        ),
    )

    companion object {
        fun parse(json: JsonElement?): WireDoc? {
            val o = json as? JsonObject ?: return null
            val kind = (o["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val uuid = (o["uuid"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val m = (o["m"] as? JsonPrimitive)?.longOrNull ?: return null
            val deleted = (o["deleted"] as? JsonPrimitive)?.booleanOrNull ?: false
            return WireDoc(kind, uuid, o["data"] as? JsonObject, m, deleted)
        }
    }
}

/** Gli errori di rete, con gli stessi significati e testi di `PeakAPIError` (PeakAPI.swift). */
sealed class PeakApiException(message: String) : Exception(message) {
    /** Il token non vale più (scollegato dal sito, o l'utente è uscito dal gruppo). */
    class Unauthorized : PeakApiException("Il collegamento a yG è scaduto: collega di nuovo.")

    /** Nessuna rete o il server non risponde. */
    class Offline : PeakApiException("Peak non è raggiungibile. Riprova con la connessione.")

    class Server(val status: Int) : PeakApiException("Peak ha risposto con un errore ($status).")

    class InvalidAnswer : PeakApiException("Risposta di Peak non valida.")

    /** L'account ha già dei dati (la prima scelta l'ha fatta un altro dispositivo). */
    class AccountNotEmpty : PeakApiException("Su Peak ci sono già dei dati.")

    /** Il file di backup non è valido o è di una versione più nuova. */
    class InvalidBackup(message: String) : PeakApiException(message)
}

data class PeakMe(val sub: String, val name: String)
data class PullResult(val rev: Int, val docs: List<WireDoc>)

/** Quelli che il server ha rifiutato perché ne ha una versione più nuova (con quella del server dentro) e le chiavi scartate perché non valide. */
data class PushResult(val rejected: List<WireDoc>, val discarded: List<String>)

/** Le chiamate che servono alla sincronizzazione e ai backup. Un'interfaccia sola per poter provare la logica con un server finto. */
interface PeakApi {
    suspend fun me(): PeakMe
    suspend fun pull(since: Int): PullResult
    suspend fun push(docs: List<WireDoc>): PushResult
    suspend fun putPhoto(sha: String, bytes: ByteArray)
    suspend fun photo(sha: String): ByteArray

    /** `first` = la prima scelta su un account vuoto: se nel frattempo un altro dispositivo ha caricato dati, risponde [PeakApiException.AccountNotEmpty]. */
    suspend fun importBackup(data: ByteArray, replace: Boolean, first: Boolean)
}

fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/**
 * Il collegamento HTTP al server. Un solo indirizzo fisso (`BuildConfig.PEAK_BASE_URL`, https://peak.yrb4g.com; nei build di debug si
 * può cambiare con `-PpeakBaseUrl=…`): il token del dispositivo non si manda mai altrove.
 */
class PeakHttp(val baseUrl: String) {
    class Response(val status: Int, val body: ByteArray)

    /** Una richiesta; un errore di rete (anche un timeout) è [PeakApiException.Offline]. */
    suspend fun request(
        path: String,
        method: String = "GET",
        query: List<Pair<String, String>> = emptyList(),
        body: ByteArray? = null,
        contentType: String? = null,
        token: String? = null,
    ): Response = withContext(Dispatchers.IO) {
        val url = StringBuilder(baseUrl.trimEnd('/')).append('/').append(path.trimStart('/'))
        if (query.isNotEmpty()) url.append('?').append(query.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" })
        try {
            val c = URL(url.toString()).openConnection() as HttpURLConnection
            try {
                c.requestMethod = method
                c.connectTimeout = 30_000
                c.readTimeout = 30_000
                c.instanceFollowRedirects = false
                if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
                if (contentType != null) c.setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    c.doOutput = true
                    c.setFixedLengthStreamingMode(body.size)
                    c.outputStream.use { it.write(body) }
                }
                val status = c.responseCode
                val stream = if (status in 200..299) c.inputStream else c.errorStream
                Response(status, stream?.use { it.readBytes() } ?: ByteArray(0))
            } finally {
                c.disconnect()
            }
        } catch (_: IOException) {
            throw PeakApiException.Offline()
        }
    }

    private fun enc(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}

/** [PeakApi] sopra HTTP, con il token del dispositivo. Come `PeakAPI` in PeakAPI.swift. */
class HttpPeakApi(private val http: PeakHttp, private val token: String) : PeakApi {
    private val json = Json

    private suspend fun send(
        path: String, method: String = "GET", query: List<Pair<String, String>> = emptyList(),
        body: ByteArray? = null, contentType: String? = null,
    ): ByteArray {
        val r = http.request(path, method, query, body, contentType, token)
        return when (r.status) {
            in 200..299 -> r.body
            401 -> throw PeakApiException.Unauthorized()
            409 -> throw PeakApiException.AccountNotEmpty()
            422 -> {
                val o = try { json.parseToJsonElement(r.body.decodeToString()) as? JsonObject } catch (_: Exception) { null }
                val version = (o?.get("version") as? JsonPrimitive)?.intOrNull
                val message = if ((o?.get("error") as? JsonPrimitive)?.contentOrNull == "versione" && version != null) {
                    "Backup in versione $version: serve una versione più recente dell'app."
                } else {
                    "Il file non è un backup di Massimali valido."
                }
                throw PeakApiException.InvalidBackup(message)
            }
            else -> throw PeakApiException.Server(r.status)
        }
    }

    private fun obj(data: ByteArray): JsonObject =
        try { json.parseToJsonElement(data.decodeToString()) as? JsonObject } catch (_: Exception) { null } ?: throw PeakApiException.InvalidAnswer()

    override suspend fun me(): PeakMe {
        val a = obj(send("api/io"))
        val sub = (a["sub"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw PeakApiException.InvalidAnswer()
        return PeakMe(sub, (a["nome"] as? JsonPrimitive)?.contentOrNull ?: "")
    }

    override suspend fun pull(since: Int): PullResult {
        val a = obj(send("api/sync", query = listOf("dopo" to since.toString())))
        val rev = (a["rev"] as? JsonPrimitive)?.intOrNull ?: throw PeakApiException.InvalidAnswer()
        val docs = a["docs"] as? JsonArray ?: throw PeakApiException.InvalidAnswer()
        return PullResult(rev, docs.mapNotNull(WireDoc::parse))
    }

    override suspend fun push(docs: List<WireDoc>): PushResult {
        val body = JsonObject(mapOf("docs" to JsonArray(docs.map { it.toJson() }))).toString().encodeToByteArray()
        val a = obj(send("api/sync", "POST", body = body, contentType = "application/json"))
        val rejected = (a["rifiutati"] as? JsonArray).orEmpty().mapNotNull(WireDoc::parse)
        val discarded = (a["scartati"] as? JsonArray).orEmpty().mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val kind = (o["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            val uuid = (o["uuid"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@mapNotNull null
            "$kind:$uuid"
        }
        return PushResult(rejected, discarded)
    }

    override suspend fun putPhoto(sha: String, bytes: ByteArray) {
        send("api/foto/$sha", "PUT", body = bytes, contentType = "image/jpeg")
    }

    override suspend fun photo(sha: String): ByteArray = send("api/foto/$sha")

    override suspend fun importBackup(data: ByteArray, replace: Boolean, first: Boolean) {
        val query = buildList {
            add("modo" to if (replace) "sostituisci" else "unisci")
            if (first) add("primo" to "1")
        }
        send("api/importa", "POST", query, data, "application/json")
    }
}
