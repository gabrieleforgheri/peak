// Generato da android/scripts/gen-assets.ts: non modificarlo a mano.

package com.gabrieleforghieri.peak.domain

/** I muscoli che la figura colora. Il raw è l'id di common/muscle-info.ts: mai rinominato, finisce nei documenti. */
enum class MuscleId(val title: String, val titleEn: String, val group: MuscleGroup) {
    chest("Petto", "Chest", MuscleGroup.petto),
    deltoids("Deltoidi", "Delts", MuscleGroup.spalle),
    trapezius("Trapezio", "Traps", MuscleGroup.schiena),
    upperBack("Schiena alta", "Upper back", MuscleGroup.schiena),
    lowerBack("Lombari", "Lower back", MuscleGroup.schiena),
    abs("Addominali", "Abs", MuscleGroup.core),
    obliques("Obliqui", "Obliques", MuscleGroup.core),
    biceps("Bicipiti", "Biceps", MuscleGroup.braccia),
    triceps("Tricipiti", "Triceps", MuscleGroup.braccia),
    forearm("Avambracci", "Forearms", MuscleGroup.braccia),
    quadriceps("Quadricipiti", "Quads", MuscleGroup.gambe),
    adductors("Adduttori", "Adductors", MuscleGroup.gambe),
    tibialis("Tibiale anteriore", "Tibialis", MuscleGroup.gambe),
    gluteal("Glutei", "Glutes", MuscleGroup.gambe),
    hamstring("Femorali", "Hamstrings", MuscleGroup.gambe),
    calves("Polpacci", "Calves", MuscleGroup.gambe);

    companion object {
        fun of(raw: String?): MuscleId? = entries.firstOrNull { it.name == raw }
    }
}
