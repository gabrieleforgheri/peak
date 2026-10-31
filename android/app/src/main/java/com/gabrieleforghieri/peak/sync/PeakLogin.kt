package com.gabrieleforghieri.peak.sync

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Il collegamento a Peak: il token di questo telefono, chi è l'utente (`sub` di authentik) e il nome da mostrare. Il token non sta mai
 * in chiaro sul disco (vedi `data/CredentialStore`); si revoca da yrb4g.com/account/peak.
 */
@Serializable
data class PeakCredentials(val token: String, val sub: String, val name: String)

/**
 * Accesso con l'account yrb4g.com, per il server di Peak. Lo stesso flusso dell'iPhone (PeakLogin.swift): il server apre il login di
 * authentik (gruppo `svc-peak`), chiede conferma («Collegare questo telefono?») e rimanda a `peak://login?code=`; il codice, con la
 * prova PKCE, diventa un token per questo dispositivo. Qui la pagina si apre in una Custom Tab e il ritorno arriva con un intent filter.
 */
object PeakLogin {
    const val CALLBACK_SCHEME = "peak"
    const val CALLBACK_HOST = "login"

    /** L'indirizzo da aprire nel browser e il verifier PKCE da tenere fino al ritorno. */
    class Start(val url: String, val verifier: String)

    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    fun start(baseUrl: String, deviceName: String, random: SecureRandom = SecureRandom()): Start {
        val bytes = ByteArray(32).also(random::nextBytes)
        val verifier = base64Url.encodeToString(bytes)
        val challenge = base64Url.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
        val name = URLEncoder.encode(deviceName, "UTF-8").replace("+", "%20")
        return Start("${baseUrl.trimEnd('/')}/auth/login?app=$challenge&nome=$name", verifier)
    }

    /** Il codice dal ritorno `peak://login?code=…`; `null` se l'indirizzo è un altro. */
    fun code(callback: String?): String? {
        val uri = try { URI(callback ?: return null) } catch (_: Exception) { return null }
        if (uri.scheme != CALLBACK_SCHEME || uri.host != CALLBACK_HOST) return null
        return uri.rawQuery?.split('&')?.mapNotNull { part ->
            val i = part.indexOf('=')
            if (i > 0 && part.substring(0, i) == "code") URLDecoder.decode(part.substring(i + 1), "UTF-8") else null
        }?.firstOrNull()
    }

    /** Scambia il codice con il token del dispositivo (`POST /auth/app/token`). */
    suspend fun credentials(http: PeakHttp, code: String, verifier: String): PeakCredentials {
        val body = JsonObject(mapOf("code" to JsonPrimitive(code), "verifier" to JsonPrimitive(verifier))).toString().encodeToByteArray()
        val r = http.request("auth/app/token", "POST", body = body, contentType = "application/json")
        val answer = if (r.status == 200) try { Json.parseToJsonElement(r.body.decodeToString()) as? JsonObject } catch (_: Exception) { null } else null
        val token = (answer?.get("token") as? JsonPrimitive)?.contentOrNull
        val sub = (answer?.get("sub") as? JsonPrimitive)?.contentOrNull
        if (token == null || sub == null) throw PeakApiException.InvalidBackup("Il collegamento è scaduto: riprova.")
        return PeakCredentials(token, sub, (answer["nome"] as? JsonPrimitive)?.contentOrNull ?: "")
    }
}

/** Dove sta il collegamento (cifrato, sul telefono). Un'interfaccia per provare la sincronizzazione senza il Keystore. */
interface CredentialStore {
    fun load(): PeakCredentials?
    fun save(credentials: PeakCredentials)
    fun clear()
}
