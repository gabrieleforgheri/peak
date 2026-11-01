package com.gabrieleforghieri.peak.ui.friends.social

import com.gabrieleforghieri.peak.sync.PeakApiException
import com.gabrieleforghieri.peak.sync.PeakHttp
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Le chiamate agli amici (`extension PeakAPI` in `Store/PeakSocial.swift`; gli indirizzi sono quelli di `server/api.ts`). Un'interfaccia sola per provare le schermate con risposte finte. */
interface SocialApi {
    suspend fun friends(): FriendLists
    suspend fun searchUsers(query: String): List<Pair<FriendPerson, FriendState>>
    suspend fun requestFriend(id: Int): FriendState
    suspend fun acceptFriend(id: Int)
    suspend fun removeFriend(id: Int)

    /** Un invito monouso: il codice in chiaro sta solo in questa risposta. */
    suspend fun createInvite(): String
    suspend fun league(): List<LeagueRow>
    suspend fun board(catalog: String): List<BoardRow>
    suspend fun news(): List<NewsEvent>
    suspend fun profile(id: Int): FriendProfileData

    /** Il link da condividere per un codice d'invito (`PeakAPI.inviteLink`). */
    fun inviteLink(code: String): String
}

/**
 * [SocialApi] sopra [PeakHttp] con il token del dispositivo: lo stesso che usa la sincronizzazione (`SyncEngine.status`). Gli errori sono
 * quelli di `HttpPeakApi` (401 = scaduto, 5xx = errore del server, rete = non raggiungibile).
 */
class HttpSocialApi(private val http: PeakHttp, private val token: String) : SocialApi {
    private val json = Json

    private suspend fun send(path: String, method: String = "GET", query: List<Pair<String, String>> = emptyList(), body: ByteArray? = null): ByteArray {
        val r = http.request(path, method, query, body, if (body != null) "application/json" else null, token)
        return when (r.status) {
            in 200..299 -> r.body
            401 -> throw PeakApiException.Unauthorized()
            else -> throw PeakApiException.Server(r.status)
        }
    }

    private fun obj(data: ByteArray): JsonObject =
        try { json.parseToJsonElement(data.decodeToString()) as? JsonObject } catch (_: Exception) { null } ?: throw PeakApiException.InvalidAnswer()

    private fun body(vararg pairs: Pair<String, JsonElement>): ByteArray = JsonObject(mapOf(*pairs)).toString().encodeToByteArray()

    override suspend fun friends(): FriendLists {
        val o = obj(send("api/amici"))
        return FriendLists(people(o["amici"]), people(o["ricevute"]), people(o["inviate"]))
    }

    override suspend fun searchUsers(query: String): List<Pair<FriendPerson, FriendState>> {
        val o = obj(send("api/amici/cerca", query = listOf("q" to query)))
        return objects(o["utenti"]).mapNotNull { u -> person(u)?.let { it to FriendState.of(string(u, "stato")) } }
    }

    override suspend fun requestFriend(id: Int): FriendState {
        val o = obj(send("api/amici", "POST", body = body("id" to JsonPrimitive(id))))
        return FriendState.of(string(o, "stato"))
    }

    override suspend fun acceptFriend(id: Int) { send("api/amici/$id/accetta", "POST", body = ByteArray(0)) }

    override suspend fun removeFriend(id: Int) { send("api/amici/$id", "DELETE") }

    override suspend fun createInvite(): String {
        val o = obj(send("api/inviti", "POST", body = ByteArray(0)))
        return string(o, "codice") ?: throw PeakApiException.InvalidAnswer()
    }

    override fun inviteLink(code: String): String = http.baseUrl.trimEnd('/') + "/invito/" + code

    override suspend fun league(): List<LeagueRow> {
        val o = obj(send("api/lega"))
        return objects(o["righe"]).mapNotNull { r ->
            val id = int(r, "id") ?: return@mapNotNull null
            val username = string(r, "username") ?: return@mapNotNull null
            LeagueRow(id, username, bool(r, "tu"), double(r, "volume30"), int(r, "sessions30") ?: 0, int(r, "streak") ?: 0, long(r, "last"))
        }
    }

    override suspend fun board(catalog: String): List<BoardRow> {
        val o = obj(send("api/classifica/$catalog"))
        return objects(o["righe"]).mapNotNull { r ->
            val id = int(r, "id") ?: return@mapNotNull null
            val username = string(r, "username") ?: return@mapNotNull null
            BoardRow(id, username, bool(r, "tu"), double(r, "oneRm"), double(r, "weight"), int(r, "reps") ?: 0)
        }
    }

    override suspend fun news(): List<NewsEvent> {
        val o = obj(send("api/novita"))
        return objects(o["eventi"]).mapNotNull { e ->
            val id = int(e, "id") ?: return@mapNotNull null
            val username = string(e, "username") ?: return@mapNotNull null
            val date = long(e, "date") ?: return@mapNotNull null
            NewsEvent(id, username, string(e, "catalog") ?: "", string(e, "name") ?: "", double(e, "weight"), int(e, "reps") ?: 0, date, bool(e, "sorpasso"))
        }
    }

    override suspend fun profile(id: Int): FriendProfileData {
        val o = obj(send("api/amici/$id"))
        val p = person(o) ?: throw PeakApiException.InvalidAnswer()
        // un massimale senza data si salta (come su iPhone), un giorno senza uuid ne prende uno nuovo
        val maxes = objects(o["maxes"]).mapNotNull { m ->
            val date = long(m, "date") ?: return@mapNotNull null
            SharedMax(string(m, "catalog") ?: "", string(m, "name") ?: "", double(m, "oneRm"), double(m, "weight"), int(m, "reps") ?: 0, date)
        }
        val templates = objects(o["templates"]).map { t ->
            SharedTemplate(
                string(t, "uuid") ?: UUID.randomUUID().toString(), string(t, "name") ?: "",
                objects(t["rows"]).map { r -> SharedRow(string(r, "catalog") ?: "", string(r, "name") ?: "", int(r, "sets") ?: 0, int(r, "repsMin") ?: 0, int(r, "repsMax") ?: 0) },
            )
        }
        return FriendProfileData(p, maxes, templates)
    }

    // ---- lettura a mano delle risposte: un campo mancante è il valore di base, come su iPhone ----

    private fun objects(v: JsonElement?): List<JsonObject> = (v as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()
    private fun people(v: JsonElement?): List<FriendPerson> = objects(v).mapNotNull(::person)
    private fun person(o: JsonObject): FriendPerson? {
        val id = int(o, "id") ?: return null
        val username = string(o, "username") ?: return null
        return FriendPerson(id, username, string(o, "name") ?: "")
    }

    private fun prim(o: JsonObject, key: String): JsonPrimitive? = o[key] as? JsonPrimitive
    private fun string(o: JsonObject, key: String): String? = prim(o, key)?.takeIf { it.isString }?.content
    private fun int(o: JsonObject, key: String): Int? = prim(o, key)?.takeIf { !it.isString }?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun long(o: JsonObject, key: String): Long? = prim(o, key)?.takeIf { !it.isString }?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }
    private fun double(o: JsonObject, key: String): Double = prim(o, key)?.takeIf { !it.isString }?.doubleOrNull ?: 0.0
    private fun bool(o: JsonObject, key: String): Boolean = prim(o, key)?.booleanOrNull ?: false
}
