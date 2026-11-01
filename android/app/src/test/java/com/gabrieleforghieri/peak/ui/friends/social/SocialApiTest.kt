package com.gabrieleforghieri.peak.ui.friends.social

import com.gabrieleforghieri.peak.sync.PeakApiException
import com.gabrieleforghieri.peak.sync.PeakHttp
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Il client degli amici contro un piccolo server HTTP locale: percorsi, metodi, token, corpi e come si leggono le risposte di `server/api.ts`. */
class SocialApiTest {
    private class Seen(val method: String, val path: String, val query: String?, val auth: String?, val type: String?, val body: String)

    private val seen = java.util.Collections.synchronizedList(mutableListOf<Seen>())
    private var status = 200
    private var reply = "{}"

    /** Un server HTTP/1.1 minimo (una richiesta per connessione): basta per vedere cosa spedisce [PeakHttp]. */
    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress()).also { socket ->
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
                    val body = ByteArray(headers["content-length"]?.toInt() ?: 0)
                    var n = 0
                    while (n < body.size) { val r = input.read(body, n, body.size - n); if (r < 0) break; n += r }
                    seen += Seen(method, target.substringBefore('?'), target.substringAfter('?', "").ifEmpty { null }, headers["authorization"], headers["content-type"], body.decodeToString())
                    val out = reply.encodeToByteArray()
                    it.getOutputStream().apply {
                        write("HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${out.size}\r\nConnection: close\r\n\r\n".encodeToByteArray())
                        write(out)
                        flush()
                    }
                }
            }
        }.apply { isDaemon = true }.start()
    }
    private val api = HttpSocialApi(PeakHttp("http://127.0.0.1:${server.localPort}"), "tok-123")

    @After fun stop() = server.close()

    private fun ok(json: String) { status = 200; reply = json }

    @Test fun friendsReadsTheThreeLists() = runTest {
        ok("""{"amici":[{"id":2,"username":"ben","name":"Ben"}],"ricevute":[{"id":3,"username":"cat","name":""}],"inviate":[{"id":4,"username":"dan"},{"rotto":1}]}""")
        val lists = api.friends()
        assertEquals(listOf(FriendPerson(2, "ben", "Ben")), lists.friends)
        assertEquals(listOf(FriendPerson(3, "cat", "")), lists.received)
        assertEquals(listOf(FriendPerson(4, "dan", "")), lists.sent)
        val s = seen.single()
        assertEquals("GET", s.method)
        assertEquals("/api/amici", s.path)
        assertEquals("Bearer tok-123", s.auth)
    }

    @Test fun searchSendsTheQueryAndReadsTheStates() = runTest {
        ok("""{"utenti":[{"id":5,"username":"ben","name":"Ben","stato":"nessuno"},{"id":6,"username":"bea","stato":"ricevuta"},{"id":7,"username":"bob","stato":"amici"},{"id":8,"username":"bru","stato":"inviata"},{"id":9,"username":"boh","stato":"altro"}]}""")
        val found = api.searchUsers("b e")
        assertEquals("/api/amici/cerca", seen.single().path)
        assertEquals("q=b%20e", seen.single().query)
        assertEquals(
            listOf(FriendState.none, FriendState.received, FriendState.friends, FriendState.sent, FriendState.none),
            found.map { it.second },
        )
        assertEquals(listOf("ben", "bea", "bob", "bru", "boh"), found.map { it.first.username })
    }

    @Test fun requestingAFriendPostsTheIdAndReturnsTheNewState() = runTest {
        ok("""{"stato":"inviata"}""")
        assertEquals(FriendState.sent, api.requestFriend(5))
        val s = seen.single()
        assertEquals("POST", s.method)
        assertEquals("/api/amici", s.path)
        assertEquals("application/json", s.type)
        assertEquals("""{"id":5}""", s.body)
    }

    @Test fun acceptAndRemoveUseTheirOwnPaths() = runTest {
        ok("""{"ok":true}""")
        api.acceptFriend(3)
        api.removeFriend(3)
        assertEquals("POST" to "/api/amici/3/accetta", seen[0].method to seen[0].path)
        assertEquals("DELETE" to "/api/amici/3", seen[1].method to seen[1].path)
    }

    @Test fun inviteGivesTheCodeAndTheLinkPointsToTheServer() = runTest {
        ok("""{"codice":"abcDEF-123","scade":1790000000000}""")
        assertEquals("abcDEF-123", api.createInvite())
        assertEquals("POST", seen.single().method)
        assertEquals("/api/inviti", seen.single().path)
        assertEquals("http://127.0.0.1:${server.localPort}/invito/abcDEF-123", api.inviteLink("abcDEF-123"))
        ok("""{"scade":1}""")
        try { api.createInvite(); fail() } catch (e: PeakApiException) { assertTrue(e is PeakApiException.InvalidAnswer) }
    }

    @Test fun leagueReadsTheRows() = runTest {
        ok("""{"righe":[{"id":1,"username":"io","tu":true,"volume30":12500.5,"sessions30":9,"streak":4,"last":1790000000000},{"id":2,"username":"ben","tu":false,"volume30":0,"sessions30":0,"streak":0,"last":null},{"username":"senza id"}]}""")
        val rows = api.league()
        assertEquals("/api/lega", seen.single().path)
        assertEquals(listOf(LeagueRow(1, "io", true, 12500.5, 9, 4, 1790000000000), LeagueRow(2, "ben", false, 0.0, 0, 0, null)), rows)
    }

    @Test fun boardAsksForTheCatalogEntry() = runTest {
        ok("""{"catalog":"bench-press-barbell","righe":[{"id":2,"username":"ben","tu":false,"name":"Panca","oneRm":110.5,"weight":100,"reps":3,"date":1}]}""")
        assertEquals(listOf(BoardRow(2, "ben", false, 110.5, 100.0, 3)), api.board("bench-press-barbell"))
        assertEquals("/api/classifica/bench-press-barbell", seen.single().path)
    }

    @Test fun newsKeepsTheOvertakeFlag() = runTest {
        ok("""{"eventi":[{"id":2,"username":"ben","catalog":"squat","name":"Squat","oneRm":150,"weight":130,"reps":4,"date":1790000000000,"sorpasso":true},{"id":3,"username":"cat","catalog":"","name":"Mio","weight":20,"reps":10,"date":5,"sorpasso":false},{"id":4,"username":"senza data"}]}""")
        val events = api.news()
        assertEquals("/api/novita", seen.single().path)
        assertEquals(2, events.size)
        assertEquals(NewsEvent(2, "ben", "squat", "Squat", 130.0, 4, 1790000000000, true), events[0])
        assertFalse(events[1].passedYou)
        assertEquals("2-squat", events[0].id)
    }

    @Test fun profileReadsMaxesAndTemplates() = runTest {
        ok(
            """{"id":2,"username":"ben","name":"Ben","maxes":[{"catalog":"squat","name":"Squat","oneRm":150,"weight":130,"reps":4,"date":10},{"catalog":"","name":"Mio","oneRm":50,"weight":40,"reps":8},{"catalog":"x","name":"y","date":3}],
            "templates":[{"uuid":"T1","name":"Gambe","rows":[{"catalog":"squat","name":"Squat","sets":3,"repsMin":6,"repsMax":8},{"name":"Mio"}]},{"name":"Senza uuid"}]}""",
        )
        val p = api.profile(2)
        assertEquals("/api/amici/2", seen.single().path)
        assertEquals(FriendPerson(2, "ben", "Ben"), p.person)
        assertEquals(listOf(SharedMax("squat", "Squat", 150.0, 130.0, 4, 10), SharedMax("x", "y", 0.0, 0.0, 0, 3)), p.maxes)
        assertEquals("T1", p.templates[0].id)
        assertEquals(listOf(SharedRow("squat", "Squat", 3, 6, 8), SharedRow("", "Mio", 0, 0, 0)), p.templates[0].rows)
        assertTrue(p.templates[1].id.isNotEmpty())
        assertTrue(p.templates[1].rows.isEmpty())
    }

    @Test fun errorsAreTheSameAsTheSync() = runTest {
        suspend fun fails(code: Int, body: String = "{}"): PeakApiException {
            status = code; reply = body
            try { api.friends(); fail("doveva lanciare") } catch (e: PeakApiException) { return e }
            error("unreachable")
        }
        assertTrue(fails(401) is PeakApiException.Unauthorized)
        assertEquals(403, (fails(403) as PeakApiException.Server).status)
        assertEquals(500, (fails(500) as PeakApiException.Server).status)
        assertTrue(fails(200, "non json") is PeakApiException.InvalidAnswer)
        status = 200; reply = """{"nome":"senza id"}"""
        try { api.profile(2); fail() } catch (e: PeakApiException) { assertTrue(e is PeakApiException.InvalidAnswer) }
        val dead = HttpSocialApi(PeakHttp("http://127.0.0.1:1"), "t")
        try { dead.friends(); fail() } catch (e: PeakApiException) { assertTrue(e is PeakApiException.Offline) }
    }

    // ---- lo store ----

    private class Fake(var broken: Boolean = false) : SocialApi by NoApi {
        var calls = 0
        override suspend fun friends(): FriendLists { calls++; if (broken) throw PeakApiException.Offline(); return FriendLists(friends = listOf(FriendPerson(2, "ben"))) }
        override suspend fun league(): List<LeagueRow> = listOf(LeagueRow(1, "io", true, 1.0, 1, 1, null))
        override suspend fun news(): List<NewsEvent> = emptyList()
    }

    @Test fun storeFillsAndFlagsFailure() = runTest {
        val fake = Fake()
        val store = SocialStore(MutableStateFlow(true)) { fake }
        assertNull(store.friends.value)
        store.refresh()
        assertEquals(1, store.friendCount)
        assertEquals(1, store.league.value!!.size)
        assertFalse(store.failed.value)
        fake.broken = true
        store.refresh()
        assertTrue("fallita: restano i dati di prima", store.failed.value && store.friendCount == 1)
        fake.broken = false
        store.reload()
        assertFalse(store.failed.value)
    }

    @Test fun storeDoesNothingWithoutAccountOrToken() = runTest {
        val fake = Fake()
        SocialStore(MutableStateFlow(false)) { fake }.also { it.refresh(); assertEquals(0, fake.calls); assertNull(it.friends.value) }
        SocialStore(MutableStateFlow(true)) { null }.also { it.refresh(); assertNull(it.friends.value); assertFalse(it.failed.value) }
    }
}

/** Un [SocialApi] che non risponde a nulla: le prove sovrascrivono solo quello che serve. */
object NoApi : SocialApi {
    override suspend fun friends() = error("non previsto")
    override suspend fun searchUsers(query: String) = error("non previsto")
    override suspend fun requestFriend(id: Int) = error("non previsto")
    override suspend fun acceptFriend(id: Int) = error("non previsto")
    override suspend fun removeFriend(id: Int) = error("non previsto")
    override suspend fun createInvite() = error("non previsto")
    override suspend fun league() = error("non previsto")
    override suspend fun board(catalog: String) = error("non previsto")
    override suspend fun news() = error("non previsto")
    override suspend fun profile(id: Int) = error("non previsto")
    override fun inviteLink(code: String) = "https://peak.yrb4g.com/invito/$code"
}
