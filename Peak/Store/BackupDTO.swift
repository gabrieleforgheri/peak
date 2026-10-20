import Foundation

/// Formato del file di backup. `version` serve a gestire eventuali cambi futuri.
struct BackupFile: Codable {
    var version: Int
    var exportedAt: Date
    var exercises: [ExerciseDTO]
    var workouts: [WorkoutDTO]
    /// Opzionale: i backup scritti prima del peso corporeo restano leggibili.
    var bodyWeights: [BodyWeightDTO]?
    /// Opzionale: i backup scritti prima delle schede restano leggibili.
    var templates: [TemplateDTO]?
    /// Opzionale: i backup scritti prima delle schede generali restano leggibili.
    var programs: [ProgramDTO]?

    static let currentVersion = 1
}

struct ExerciseDTO: Codable {
    var uuid: UUID
    var name: String
    var muscleGroup: String
    var notes: String
    var incrementStep: Double
    var sortIndex: Int
    var isArchived: Bool
    var createdAt: Date
    /// Opzionale: i backup scritti prima dei pittogrammi restano leggibili.
    var glyph: String?
    /// Foto in base64. Opzionale per lo stesso motivo.
    var photo: String?
    /// Inquadratura della foto. Opzionale: i backup più vecchi non ce l'hanno.
    var photoOffset: Double?
    /// Opzionale: i backup più vecchi non distinguono mono e bilaterale.
    var isUnilateral: Bool?
    /// Opzionale per lo stesso motivo; assente = recupero delle impostazioni.
    var restSeconds: Int?
    /// Opzionali: i backup scritti prima del catalogo non li hanno.
    var catalogID: String?
    var isHidden: Bool?
    /// Opzionali: i backup scritti prima della figura dei muscoli non li hanno.
    var muscles: [String]?
    var view: String?
    /// Opzionale: creato a mano di proposito.
    var isCustom: Bool?
    /// Opzionali: peso sbarra e modalità di visualizzazione (assente = 0, absolute).
    var barWeight: Double?
    var weightDisplay: String?
    var records: [MaxRecordDTO]
}

struct MaxRecordDTO: Codable {
    var uuid: UUID
    var date: Date
    var weight: Double
    var reps: Int
    var isEstimated: Bool
    var note: String
}

struct WorkoutDTO: Codable {
    var uuid: UUID
    var date: Date
    var endedAt: Date?
    var note: String
    /// Opzionale: i backup scritti prima del focus restano leggibili.
    var focus: String?
    /// Opzionale: i backup scritti prima dei nomi restano leggibili.
    var name: String?
    /// Opzionali: i backup scritti prima delle schede non li hanno.
    var template: UUID?
    var plan: [TemplateItemDTO]?
    var sets: [WorkoutSetDTO]
}

struct TemplateDTO: Codable {
    var uuid: UUID
    var name: String
    var sortIndex: Int
    var createdAt: Date
    var isHidden: Bool?
    /// Opzionali: i backup scritti prima delle schede generali non li hanno.
    var icon: String?
    var programID: UUID?
    var weekday: Int?
    var items: [TemplateItemDTO]
}

struct ProgramDTO: Codable {
    var uuid: UUID
    var name: String
    var sortIndex: Int
    var createdAt: Date
    var useDays: Bool?
}

/// Una riga di scheda. `weight` assente = si mostra l'ultimo usato.
struct TemplateItemDTO: Codable {
    var exerciseUUID: UUID
    var sets: Int
    var repsMin: Int
    var repsMax: Int
    var weight: Double?

    init(_ item: TemplateItem) {
        exerciseUUID = item.exercise
        sets = item.sets
        repsMin = item.repsMin
        repsMax = item.repsMax
        weight = item.weight
    }

    var item: TemplateItem {
        TemplateItem(exercise: exerciseUUID, sets: sets, repsMin: repsMin, repsMax: repsMax, weight: weight)
    }
}

struct WorkoutSetDTO: Codable {
    var uuid: UUID
    /// Riferimento all'esercizio tramite uuid: resiste all'import su un database diverso.
    var exerciseUUID: UUID?
    var weight: Double
    var reps: Int
    var rpe: Double?
    var isWarmup: Bool
    var order: Int
    var createdAt: Date
    var isPersonalRecord: Bool
    /// Opzionale: assente = la serie conta per i record. Le versioni 1.x lo ignorano.
    var excludedFromRecords: Bool?
}

struct BodyWeightDTO: Codable {
    var uuid: UUID
    var date: Date
    var weight: Double
    var fromHealth: Bool
}
