package com.gabrieleforghieri.peak.ui.friends.social

/**
 * Gli amici: tutto passa dal server (ogni percorso controlla l'amicizia e toglie i nascosti). I tipi di `Store/PeakSocial.swift`;
 * le date arrivano in millisecondi dall'epoca, come in tutta l'app.
 */

data class FriendPerson(val id: Int, val username: String, val name: String = "")

/** Lo stato di un utente rispetto a te: i valori sono quelli del server (`stato`). */
enum class FriendState(val raw: String) {
    none("nessuno"), friends("amici"), sent("inviata"), received("ricevuta");

    companion object {
        fun of(raw: String?): FriendState = entries.firstOrNull { it.raw == raw } ?: none
    }
}

data class FriendLists(
    val friends: List<FriendPerson> = emptyList(),
    val received: List<FriendPerson> = emptyList(),
    val sent: List<FriendPerson> = emptyList(),
)

data class LeagueRow(
    val id: Int,
    val username: String,
    val isMe: Boolean,
    val volume30: Double,
    val sessions30: Int,
    val streak: Int,
    val last: Long?,
)

data class SharedMax(val catalog: String, val name: String, val oneRm: Double, val weight: Double, val reps: Int, val date: Long) {
    val id: String get() = catalog + name
}

data class SharedRow(val catalog: String, val name: String, val sets: Int, val repsMin: Int, val repsMax: Int)

data class SharedTemplate(val id: String, val name: String, val rows: List<SharedRow>)

data class FriendProfileData(val person: FriendPerson, val maxes: List<SharedMax>, val templates: List<SharedTemplate>)

data class BoardRow(val id: Int, val username: String, val isMe: Boolean, val oneRm: Double, val weight: Double, val reps: Int)

data class NewsEvent(
    val friendID: Int,
    val username: String,
    val catalog: String,
    val name: String,
    val weight: Double,
    val reps: Int,
    val date: Long,
    val passedYou: Boolean,
) {
    val id: String get() = "$friendID-$catalog"
}
