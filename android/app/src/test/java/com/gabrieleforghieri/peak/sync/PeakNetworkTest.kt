package com.gabrieleforghieri.peak.sync

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PeakLoginTest {
    private val base = "https://peak.yrb4g.com"

    @Test fun theLoginAddressCarriesTheChallengeAndTheDeviceName() {
        val start = PeakLogin.start(base, "Pixel di Gabriele & co")
        val uri = URI(start.url)
        assertEquals("https", uri.scheme)
        assertEquals("peak.yrb4g.com", uri.host)
        assertEquals("/auth/login", uri.path)
        val query = uri.rawQuery.split('&').associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        assertEquals(setOf("app", "nome"), query.keys)
        assertEquals("Pixel di Gabriele & co", query["nome"])
        // sfida PKCE: sha256 del verifier in base64url senza riempimento; il server la vuole di 43 caratteri
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(start.verifier.encodeToByteArray()))
        assertEquals(expected, query["app"])
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(query["app"]!!))
        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(start.verifier)) // il server accetta verifier fra 43 e 128 caratteri
    }

    @Test fun everyLoginHasItsOwnVerifier() {
        assertTrue(PeakLogin.start(base, "x").verifier != PeakLogin.start(base, "x").verifier)
    }

    @Test fun aTrailingSlashInTheBaseIsHarmless() {
        assertTrue(PeakLogin.start("http://10.0.2.2:3416/", "x").url.startsWith("http://10.0.2.2:3416/auth/login?app="))
    }

    @Test fun theCodeComesFromThePeakLoginCallback() {
        assertEquals("abc-DEF_123", PeakLogin.code("peak://login?code=abc-DEF_123"))
        assertEquals("a b", PeakLogin.code("peak://login?x=1&code=a%20b"))
        assertNull(PeakLogin.code("peak://login"))
        assertNull(PeakLogin.code("peak://altro?code=abc"))
        assertNull(PeakLogin.code("https://peak.yrb4g.com/login?code=abc"))
        assertNull(PeakLogin.code("peak://login?codice=abc"))
        assertNull(PeakLogin.code("non un indirizzo"))
        assertNull(PeakLogin.code(null))
    }
}

/** Le chiamate vere contro un piccolo server HTTP locale: percorsi, intestazioni, corpi e come si leggono gli errori. */
class PeakNetworkTest {
    private class Seen(val method: String, val path: String, val query: String?, val auth: String?, val type: String?, val body: ByteArray)

    private val seen = java.util.Collections.synchronizedList(mutableListOf<Seen>())
    private var answer: (Seen) -> Triple<Int, String, ByteArray> = { Triple(200, "application/json", "{}".encodeToByteArray()) }

    /** Un server HTTP/1.1 minimo (una richiesta per connessione): basta per vedere cosa spedisce [PeakHttp]. */
    private val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress()).also { socket ->
        Thread {
            while (!socket.isClosed) {
                val client = try { socket.accept() } catch (_: java.io.IOException) { break }
                client.use {
                    val input = it.getInputStream()
                    fun line(): String {
                        val out = ByteArrayOutputStream()
                        while (true) { val b = input.read(); if (b < 0 || b == '\n'.code) break; if (b != '\r'.code) out.write(b) }
                        return out.toString(Charsets.UTF_8)
                    }
                    val (method, target) = line().split(' ')
                    val headers = generateSequence { line().ifEmpty { null } }.associate { h -> h.substringBefore(':').lowercase() to h.substringAfter(':').trim() }
                    val body = ByteArray(headers["content-length"]?.toInt() ?: 0).also { b -> readFully(input, b) }
                    val s = Seen(method, target.substringBefore('?'), target.substringAfter('?', "").ifEmpty { null }, headers["authorization"], headers["content-type"], body)
                    seen += s
                    val (status, type, reply) = answer(s)
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $status X\r\nContent-Type: $type\r\nContent-Length: ${reply.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                        write(reply)
                        flush()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }

    private fun readFully(input: InputStream, into: ByteArray) {
        var n = 0
        while (n < into.size) { val r = input.read(into, n, into.size - n); if (r < 0) break; n += r }
    }

    private val http = PeakHttp("http://127.0.0.1:${server.localPort}")
    private val api = HttpPeakApi(http, "tok-123")

    @After fun stop() = server.close()

    private fun reply(status: Int, json: String) { answer = { Triple(status, "application/json", json.encodeToByteArray()) } }

    private fun doc(kind: String, uuid: String, m: Long, deleted: Boolean = false) = WireDoc(kind, uuid, if (deleted) null else JsonObject(mapOf("name" to JsonPrimitive("x"))), m, deleted)

    @Test fun meReadsTheAccount() = runTest {
        reply(200, """{"sub":"s-1","nome":"Gabriele","username":"gab","vapid":"k"}""")
        assertEquals(PeakMe("s-1", "Gabriele"), api.me())
        assertEquals("GET", seen.single().method)
        assertEquals("/api/io", seen.single().path)
        assertEquals("Bearer tok-123", seen.single().auth)
    }

    @Test fun pullAsksForTheCursorAndReadsTheDocuments() = runTest {
        reply(200, """{"rev":42,"docs":[{"kind":"exercise","uuid":"U1","data":{"name":"a"},"m":5,"deleted":false},{"kind":"set","uuid":"U2","data":null,"m":6,"deleted":true},{"rotto":1}]}""")
        val r = api.pull(17)
        assertEquals("/api/sync", seen.single().path)
        assertEquals("dopo=17", seen.single().query)
        assertEquals(42, r.rev)
        assertEquals(listOf(WireDoc("exercise", "U1", JsonObject(mapOf("name" to JsonPrimitive("a"))), 5, false), WireDoc("set", "U2", null, 6, true)), r.docs)
    }

    @Test fun pushSendsTheDocsAndReadsRejectedAndDiscarded() = runTest {
        reply(200, """{"rev":9,"rifiutati":[{"kind":"exercise","uuid":"U1","data":{"name":"del server"},"m":99,"deleted":false}],"scartati":[{"kind":"set","uuid":"U3","errore":"weight"},{"x":1}]}""")
        val r = api.push(listOf(doc("exercise", "U1", 5), doc("set", "U3", 6), doc("workout", "U4", 7, deleted = true)))
        val s = seen.single()
        assertEquals("POST", s.method)
        assertEquals("/api/sync", s.path)
        assertEquals("application/json", s.type)
        assertEquals("Bearer tok-123", s.auth)
        val sent = (Json.parseToJsonElement(s.body.decodeToString()) as JsonObject)["docs"] as JsonArray
        assertEquals(3, sent.size)
        assertEquals(JsonPrimitive(5), (sent[0] as JsonObject)["m"])
        assertEquals(JsonPrimitive(true), (sent[2] as JsonObject)["deleted"])
        assertEquals(kotlinx.serialization.json.JsonNull, (sent[2] as JsonObject)["data"])
        assertEquals(listOf("exercise:U1"), r.rejected.map { it.key })
        assertEquals(99, r.rejected.single().m)
        assertEquals(listOf("set:U3"), r.discarded)
    }

    @Test fun photosAreAddressedByTheirSha() = runTest {
        val sha = "ab".repeat(32)
        answer = { s -> if (s.method == "GET") Triple(200, "image/jpeg", byteArrayOf(1, 2, 3)) else Triple(200, "application/json", """{"ok":true}""".encodeToByteArray()) }
        assertEquals(listOf<Byte>(1, 2, 3), api.photo(sha).toList())
        api.putPhoto(sha, byteArrayOf(9, 8))
        assertEquals("/api/foto/$sha", seen[0].path)
        val put = seen[1]
        assertEquals("PUT", put.method)
        assertEquals("/api/foto/$sha", put.path)
        assertEquals("image/jpeg", put.type)
        assertEquals(listOf<Byte>(9, 8), put.body.toList())
    }

    @Test fun importUsesTheModeAndTheFirstChoiceFlag() = runTest {
        reply(200, """{"conteggi":{},"orfani":0,"scartati":0,"rev":1}""")
        api.importBackup("{}".encodeToByteArray(), replace = false, first = true)
        api.importBackup("{}".encodeToByteArray(), replace = true, first = false)
        assertEquals("/api/importa", seen[0].path)
        assertEquals("modo=unisci&primo=1", seen[0].query)
        assertEquals("modo=sostituisci", seen[1].query)
        assertEquals("application/json", seen[0].type)
        assertEquals("POST", seen[0].method)
    }

    @Test fun statusCodesBecomeTheSameErrorsAsOnIphone() = runTest {
        suspend fun fails(status: Int, body: String = "{}"): PeakApiException {
            reply(status, body)
            try { api.me(); fail("doveva lanciare") } catch (e: PeakApiException) { return e }
            error("unreachable")
        }
        assertTrue(fails(401) is PeakApiException.Unauthorized)
        assertTrue(fails(409, """{"error":"account non vuoto"}""") is PeakApiException.AccountNotEmpty)
        assertEquals(500, (fails(500) as PeakApiException.Server).status)
        assertEquals("Peak ha risposto con un errore (503).", fails(503).message)
        assertEquals("Il collegamento a yG è scaduto: collega di nuovo.", fails(401).message)
        val bad = fails(422, """{"error":"invalido"}""")
        assertTrue(bad is PeakApiException.InvalidBackup)
        assertEquals("Il file non è un backup di Massimali valido.", bad.message)
        assertEquals("Backup in versione 3: serve una versione più recente dell'app.", fails(422, """{"error":"versione","version":3}""").message)
        assertEquals("Il file non è un backup di Massimali valido.", fails(422, """{"error":"scartati","scartati":2}""").message)
        reply(200, "non json")
        try { api.me(); fail() } catch (e: PeakApiException) { assertTrue(e is PeakApiException.InvalidAnswer) }
        reply(200, """{"nome":"senza sub"}""")
        try { api.me(); fail() } catch (e: PeakApiException) { assertTrue(e is PeakApiException.InvalidAnswer) }
    }

    @Test fun noServerMeansOffline() = runTest {
        val dead = HttpPeakApi(PeakHttp("http://127.0.0.1:1"), "t")
        try { dead.me(); fail() } catch (e: PeakApiException) { assertTrue(e is PeakApiException.Offline) }
        assertEquals("Peak non è raggiungibile. Riprova con la connessione.", PeakApiException.Offline().message)
    }

    @Test fun theLoginCodeBecomesACredential() = runTest {
        reply(200, """{"token":"T0K","sub":"sub-9","nome":"Gabriele"}""")
        assertEquals(PeakCredentials("T0K", "sub-9", "Gabriele"), PeakLogin.credentials(http, "codice", "verifier-xyz"))
        val s = seen.single()
        assertEquals("POST", s.method)
        assertEquals("/auth/app/token", s.path)
        assertNull(s.auth)
        assertEquals(JsonObject(mapOf("code" to JsonPrimitive("codice"), "verifier" to JsonPrimitive("verifier-xyz"))), Json.parseToJsonElement(s.body.decodeToString()))
    }

    @Test fun aWrongOrExpiredCodeIsAFriendlyError() = runTest {
        reply(400, """{"error":"codice"}""")
        try { PeakLogin.credentials(http, "x", "y"); fail() } catch (e: PeakApiException) { assertEquals("Il collegamento è scaduto: riprova.", e.message) }
        reply(200, """{"sub":"senza token"}""")
        try { PeakLogin.credentials(http, "x", "y"); fail() } catch (e: PeakApiException) { assertEquals("Il collegamento è scaduto: riprova.", e.message) }
    }
}
