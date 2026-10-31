package com.gabrieleforghieri.peak.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Il verifier PKCE tenuto fra l'apertura della Custom Tab e il ritorno `peak://login` (il processo può morire nel frattempo). */
interface PendingLogin {
    fun save(verifier: String)

    /** Lo restituisce e lo cancella: vale una volta sola. */
    fun take(): String?
}

/**
 * Le scelte del primo collegamento a Peak (yG). Prima di toccare qualcosa sul telefono si scrive `Peak-prima-del-collegamento.json`; il
 * token si salva solo a scelta finita ([SyncEngine.completeConnection]). Traduzione di `ConnectFlow.swift`.
 *
 *  - server vuoto: carica i dati del telefono, importa un backup, oppure parti da zero;
 *  - server con dati e telefono con dati propri: «Usa i dati del server» oppure «Unisci»;
 *  - server con dati e telefono solo col catalogo: si prendono i dati del server, senza domande.
 *
 * Su Android il login non è una chiamata che aspetta: [begin] dà l'indirizzo da aprire in una Custom Tab, e il ritorno arriva a [handleCallback].
 */
class ConnectFlow(
    private val sync: SyncEngine,
    private val http: PeakHttp,
    private val pending: PendingLogin,
    private val deviceName: () -> String,
    private val isFallback: () -> Boolean,
) {
    sealed class Stage {
        object Idle : Stage()
        data class Working(val message: String) : Stage()

        /** `hasLocalData`: il telefono ha macchinari, allenamenti, massimali o pesate da caricare. */
        data class EmptyServer(val hasLocalData: Boolean) : Stage()
        object Conflict : Stage()
        data class Failed(val message: String) : Stage()
        object Done : Stage()
    }

    private val _stage = MutableStateFlow<Stage>(Stage.Idle)
    val stage: StateFlow<Stage> = _stage

    /** Controlla che si possa collegare e dà l'indirizzo di login da aprire; `null` se no (lo stato dice perché). */
    suspend fun begin(): String? {
        if (isFallback()) {
            _stage.value = Stage.Failed("L'archivio dell'app non si è aperto e i dati non sono al sicuro. Chiudi e riapri Peak prima di collegare l'account.")
            return null
        }
        if (sync.hasActiveWorkout()) {
            _stage.value = Stage.Failed("C'è un allenamento in corso. Terminalo e poi collega l'account.")
            return null
        }
        val start = PeakLogin.start(http.baseUrl, deviceName())
        pending.save(start.verifier)
        _stage.value = Stage.Working("Accesso a yG…")
        return start.url
    }

    /** Il ritorno `peak://login?code=…`: scambia il codice con il token e passa alla scelta. Un altro indirizzo si ignora. */
    suspend fun handleCallback(callback: String?) {
        val code = PeakLogin.code(callback) ?: return
        val verifier = pending.take()
        if (verifier == null) { _stage.value = Stage.Failed("Il collegamento è scaduto: riprova."); return }
        _stage.value = Stage.Working("Accesso a yG…")
        try {
            connectWith(PeakLogin.credentials(http, code, verifier))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sync.cancelConnection()
            _stage.value = Stage.Failed(e.message ?: "Collegamento non riuscito.")
        }
    }

    /** Dal token in poi: riprende un collegamento scaduto, oppure scrive la copia di sicurezza e passa alla scelta. Lancia se qualcosa non va. */
    suspend fun connectWith(credentials: PeakCredentials) {
        // Collegamento scaduto, stesso account: si riprende da dove si era, senza domande.
        if (sync.renew(credentials)) {
            sync.syncNow()
            _stage.value = Stage.Done
            return
        }
        // La copia di sicurezza prima di qualunque cosa: se non si riesce a scriverla, non si va avanti.
        if (sync.localHasExercises()) sync.writePreConnectionBackup()
        sync.connect(credentials)
        decide()
    }

    fun cancel() {
        pending.take()
        sync.cancelConnection()
        _stage.value = Stage.Idle
    }

    /** Dopo un esito (fatto o errore) la schermata torna a riposo. */
    fun dismiss() { if (_stage.value is Stage.Done || _stage.value is Stage.Failed) _stage.value = Stage.Idle }

    private suspend fun decide() {
        _stage.value = Stage.Working("Controllo i dati su Peak…")
        val serverHasData = sync.inspectServer()
        if (!serverHasData) {
            _stage.value = Stage.EmptyServer(hasLocalData = sync.localHasExercises() || sync.localHasUserData())
        } else if (sync.localHasUserData()) {
            _stage.value = Stage.Conflict
        } else {
            run("Scarico i tuoi dati…") { sync.replaceLocalWithServer() }
        }
    }

    // ---- le scelte --------------------------------------------------------------------------------------

    suspend fun uploadLocalData() = run("Carico i dati di questo telefono…") {
        sync.uploadLocalData(replace = false, first = true)
        sync.replaceLocalWithServer()
    }

    /** Account vuoto, niente da caricare: si collega e basta. */
    suspend fun startEmpty() = run("Collego l'account…") { sync.replaceLocalWithServer() }

    suspend fun importBackup(data: ByteArray) = run("Importo il backup…") {
        sync.uploadBackup(data, replace = false, first = true)
        sync.replaceLocalWithServer()
    }

    suspend fun useServerData() = run("Scarico i dati da Peak…") { sync.replaceLocalWithServer() }

    suspend fun merge() = run("Unisco i dati…") {
        sync.uploadLocalData(replace = false, first = false)
        sync.replaceLocalWithServer()
    }

    /** Esegue un passo, poi chiude il collegamento. Un errore lascia il telefono com'era (la rete va prima delle modifiche locali). */
    private suspend fun run(message: String, step: suspend () -> Unit) {
        _stage.value = Stage.Working(message)
        try {
            step()
            sync.completeConnection()
            sync.syncNow()
            _stage.value = Stage.Done
        } catch (_: PeakApiException.AccountNotEmpty) {
            // Un altro dispositivo ha fatto la prima scelta mentre si decideva: si riparte dal confronto.
            try { decide() } catch (e: Exception) { _stage.value = Stage.Failed(e.message ?: "Collegamento non riuscito.") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _stage.value = Stage.Failed(e.message ?: "Collegamento non riuscito.")
        }
    }
}
