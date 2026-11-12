import Foundation
import SwiftData

// Versioni dello schema SwiftData.
//
// Regole, perché gli store degli utenti sono veri:
//  - Ogni modifica a un `@Model` (proprietà aggiunta, tolta, tipo cambiato, relazione)
//    richiede un nuovo `PeakSchemaV2…` con la sua copia dei modelli e uno `stage` in
//    `PeakMigrationPlan.stages`. Non si modifica mai in silenzio `PeakSchemaV1`.
//  - Un rename è visto da SwiftData come cancellazione più creazione, cioè dati persi:
//    usare `@Attribute(originalName:)` sulla proprietà rinominata.
//  - Aggiungere una versione per cambi che SwiftData gestisce da solo (per esempio una
//    proprietà nuova con default) può dare l'errore «Duplicate version checksums»: in quel
//    caso la migrazione leggera non va dichiarata come versione a parte.
//  - Prima di rilasciare, la migrazione va provata su iPhone con una copia del backup JSON.

/// Prima versione dichiarata. Descrive lo schema già in uso prima che esistessero le versioni.
enum PeakSchemaV1: VersionedSchema {
    static var versionIdentifier: Schema.Version { Schema.Version(1, 0, 0) }

    static var models: [any PersistentModel.Type] {
        [
            Exercise.self,
            MaxRecord.self,
            Workout.self,
            WorkoutSet.self,
            BodyWeightEntry.self,
            WorkoutTemplate.self,
            WorkoutProgram.self
        ]
    }
}

enum PeakMigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [PeakSchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}
