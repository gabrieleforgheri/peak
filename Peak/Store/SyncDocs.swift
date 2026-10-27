import Foundation
import SwiftData

/// I documenti sincronizzati con il server di Peak: come i modelli SwiftData diventano documenti
/// (`settingDocs` + `encodeModels`, insieme `encodeAll`) e come quelli che arrivano diventano modelli (`apply`).
///
/// Cosa NON viaggia mai: i massimali "da allenamento" (`MaxRecord.isEstimated`) e il flag
/// `isPersonalRecord`: si ricalcolano dalle serie (RecordService), uguali su ogni dispositivo.
/// I pesi sono sempre in kg, le date a secondi interi (PeakDates), gli uuid in maiuscolo.
@MainActor
enum SyncDocs {

    /// Chiave → contenuto del documento, pronto per il JSON.
    typealias Encoded = [String: [String: Any]]

    /// Ordine di applicazione: prima i genitori (macchinari, allenamenti), poi ciò che li richiama.
    static let applyOrder = ["setting", "exercise", "program", "template", "workout", "record", "set", "bodyweight"]

    nonisolated static func key(_ kind: String, _ uuid: UUID) -> String { "\(kind):\(uuid.uuidString)" }

    // MARK: - Impostazioni condivise

    /// Le impostazioni che seguono l'account. Quelle del Watch, di Salute e dei dati interni restano del dispositivo.
    static let settingKeys = [
        "unit", "accent", "restTimerEnabled", "restSeconds", "progressionEnabled", "repRangeMin",
        "repRangeMax", "plateCalculatorEnabled", "availablePlates", "tutorialDone", "tourSeen", "machineTipsSeen", "weeklyGoal", "language", "activeProgram"
    ]

    private static func settingValue(_ name: String, _ settings: AppSettings) -> Any {
        switch name {
        case "unit": return settings.unit.rawValue
        case "accent": return settings.accent.rawValue
        case "restTimerEnabled": return settings.restTimerEnabled
        case "restSeconds": return settings.restSeconds
        case "progressionEnabled": return settings.progressionEnabled
        case "repRangeMin": return settings.repRangeMin
        case "repRangeMax": return settings.repRangeMax
        case "plateCalculatorEnabled": return settings.plateCalculatorEnabled
        case "barWeight": return settings.barWeight
        case "availablePlates": return settings.availablePlates
        case "tutorialDone": return settings.tutorialDone
        case "tourSeen": return settings.tourSeen
        case "machineTipsSeen": return settings.machineTipsSeen
        case "weeklyGoal": return settings.weeklyGoal
        case "language": return settings.language
        case "activeProgram": return settings.activeProgram
        default: return NSNull()
        }
    }

    private static func applySetting(_ name: String, _ value: Any?, to settings: AppSettings) -> Bool {
        func number(_ lo: Double, _ hi: Double) -> Double? {
            (value as? NSNumber).map(\.doubleValue).flatMap { $0 >= lo && $0 <= hi ? $0 : nil }
        }
        switch name {
        case "unit": guard let raw = value as? String, let v = WeightUnit(rawValue: raw) else { return false }; settings.unit = v
        case "accent": guard let raw = value as? String, let v = AccentPalette(rawValue: raw) else { return false }; settings.accent = v
        case "restTimerEnabled": guard let v = value as? Bool else { return false }; settings.restTimerEnabled = v
        case "restSeconds": guard let v = number(15, 600) else { return false }; settings.restSeconds = Int(v)
        case "progressionEnabled": guard let v = value as? Bool else { return false }; settings.progressionEnabled = v
        case "repRangeMin": guard let v = number(1, 30) else { return false }; settings.repRangeMin = Int(v)
        case "repRangeMax": guard let v = number(1, 30) else { return false }; settings.repRangeMax = Int(v)
        case "plateCalculatorEnabled": guard let v = value as? Bool else { return false }; settings.plateCalculatorEnabled = v
        case "barWeight": guard let v = number(0, 100) else { return false }; settings.barWeight = v
        case "tutorialDone": guard let v = value as? Bool else { return false }; settings.tutorialDone = v
        case "tourSeen": guard let v = number(1, 10) else { return false }; settings.tourSeen = Int(v)
        case "machineTipsSeen": guard let v = value as? Bool else { return false }; settings.machineTipsSeen = v
        case "weeklyGoal": guard let v = number(0, 7) else { return false }; settings.weeklyGoal = Int(v)
        case "language": guard let v = value as? String, ["", "it", "en"].contains(v) else { return false }; settings.language = v
        case "activeProgram": guard let v = value as? String, v.isEmpty || UUID(uuidString: v) != nil else { return false }; settings.activeProgram = v.uppercased()
        case "availablePlates":
            guard let list = value as? [NSNumber] else { return false }
            settings.availablePlates = list.map(\.doubleValue)
        default: return false
        }
        return true
    }

    // MARK: - Codifica

    /// Le impostazioni: poche e economiche, restano sul main.
    static func settingDocs(_ settings: AppSettings) -> Encoded {
        var out: Encoded = [:]
        for name in settingKeys {
            out["setting:\(name)"] = ["value": settingValue(name, settings)]
        }
        return out
    }

    /// Tutto il resto, da un contesto qualunque (anche creato fuori dal main: la sincronizzazione lo fa per
    /// non bloccare l'interfaccia). `photoSha` dà lo sha256 della foto di un macchinario.
    nonisolated static func encodeModels(context: ModelContext, photoSha: (Exercise) -> String) throws -> Encoded {
        var out: Encoded = [:]

        for exercise in try context.fetch(FetchDescriptor<Exercise>()) {
            out[key("exercise", exercise.uuid)] = [
                "name": exercise.name,
                "muscleGroup": exercise.muscleGroupRaw,
                "notes": exercise.notes,
                "incrementStep": exercise.incrementStep,
                "sortIndex": exercise.sortIndex,
                "isArchived": exercise.isArchived,
                "createdAt": PeakDates.string(exercise.createdAt),
                "glyph": exercise.glyphRaw,
                "photo": photoSha(exercise),
                "photoOffset": exercise.photoOffset,
                "isUnilateral": exercise.isUnilateral,
                "restSeconds": exercise.restSeconds,
                "catalog": exercise.catalogID,
                "hidden": exercise.isHidden,
                "muscles": exercise.muscleIDs.map(\.rawValue),
                "view": exercise.viewRaw,
                "custom": exercise.isCustom,
                "barWeight": exercise.barWeight,
                "weightDisplay": exercise.weightDisplay,
                "plateLoaded": exercise.plateLoaded,
                "perSide": exercise.perSide
            ]
            // solo i massimali inseriti a mano: gli altri si ricalcolano dalle serie
            for record in exercise.recordList where !record.isEstimated {
                out[key("record", record.uuid)] = [
                    "exercise": exercise.uuid.uuidString,
                    "date": PeakDates.string(record.date),
                    "weight": record.weight,
                    "reps": record.reps,
                    "note": record.note,
                    "singleArm": record.singleArm
                ]
            }
        }

        for workout in try context.fetch(FetchDescriptor<Workout>()) {
            out[key("workout", workout.uuid)] = [
                "date": PeakDates.string(workout.date),
                "endedAt": workout.endedAt.map(PeakDates.string) ?? NSNull(),
                "note": workout.note,
                "name": workout.name,
                "focus": workout.focusRaw,
                "template": workout.templateRaw,
                "plan": TemplateItems.json(workout.plan)
            ]
            for set in workout.setList {
                out[key("set", set.uuid)] = [
                    "workout": workout.uuid.uuidString,
                    "exercise": set.exercise?.uuid.uuidString ?? NSNull(),
                    "weight": set.weight,
                    "reps": set.reps,
                    "rpe": set.rpe ?? NSNull(),
                    "isWarmup": set.isWarmup,
                    "order": set.order,
                    "createdAt": PeakDates.string(set.createdAt),
                    "noRecord": set.excludedFromRecords,
                    "singleArm": set.singleArm
                ]
            }
        }

        for template in try context.fetch(FetchDescriptor<WorkoutTemplate>()) {
            out[key("template", template.uuid)] = [
                "name": template.name,
                "items": TemplateItems.json(template.items),
                "sortIndex": template.sortIndex,
                "createdAt": PeakDates.string(template.createdAt),
                "hidden": template.isHidden,
                "icon": template.iconRaw,
                "program": template.programRaw,
                "weekday": template.weekday
            ]
        }

        for program in try context.fetch(FetchDescriptor<WorkoutProgram>()) {
            out[key("program", program.uuid)] = [
                "name": program.name,
                "sortIndex": program.sortIndex,
                "createdAt": PeakDates.string(program.createdAt),
                "useDays": program.useDays
            ]
        }

        for entry in try context.fetch(FetchDescriptor<BodyWeightEntry>()) {
            out[key("bodyweight", entry.uuid)] = [
                "date": PeakDates.string(entry.date),
                "weight": entry.weight,
                "fromHealth": entry.fromHealth
            ]
        }
        return out
    }

    /// Impostazioni e modelli insieme, sul contesto principale: per ciò che si è appena applicato.
    static func encodeAll(context: ModelContext, settings: AppSettings, photoSha: (Exercise) -> String) throws -> Encoded {
        try settingDocs(settings).merging(encodeModels(context: context, photoSha: photoSha)) { first, _ in first }
    }

    /// Il JSON del documento, a chiavi ordinate: lo stesso contenuto dà sempre gli stessi byte.
    nonisolated static func bytes(_ data: [String: Any]) -> Data {
        (try? JSONSerialization.data(withJSONObject: data, options: [.sortedKeys])) ?? Data()
    }

    /// Hash del contenuto (sha256 di `bytes`): lo stesso contenuto dà sempre lo stesso hash.
    nonisolated static func hash(_ data: [String: Any]) -> String { PeakAPI.sha256(bytes(data)) }

    // MARK: - Applicazione

    struct Applied {
        var upserted: Set<String> = []
        var deleted: Set<String> = []
        /// Documenti che non si possono applicare (il genitore non c'è): si ricordano, ma non si cancellano mai.
        var skipped: Set<String> = []
    }

    /// Applica i documenti arrivati dal server ai modelli, in una volta sola. `photos` ha i byte
    /// delle foto già scaricate (per sha256). Non salva e non ricalcola i record: lo fa il chiamante.
    static func apply(_ docs: [WireDoc], context: ModelContext, settings: AppSettings, photos: [String: Data]) throws -> Applied {
        var result = Applied()

        func index<T: PersistentModel>(_ type: T.Type, _ uuid: KeyPath<T, UUID>) throws -> [UUID: T] {
            Dictionary(try context.fetch(FetchDescriptor<T>()).map { ($0[keyPath: uuid], $0) }, uniquingKeysWith: { first, _ in first })
        }
        var exercises = try index(Exercise.self, \.uuid)
        var workouts = try index(Workout.self, \.uuid)
        var templates = try index(WorkoutTemplate.self, \.uuid)
        var programs = try index(WorkoutProgram.self, \.uuid)
        var records = try index(MaxRecord.self, \.uuid)
        var sets = try index(WorkoutSet.self, \.uuid)
        var weights = try index(BodyWeightEntry.self, \.uuid)

        func text(_ d: [String: Any], _ k: String) -> String? { d[k] as? String }
        func number(_ d: [String: Any], _ k: String) -> Double? { (d[k] as? NSNumber)?.doubleValue }
        func integer(_ d: [String: Any], _ k: String) -> Int? { (d[k] as? NSNumber)?.intValue }
        func flag(_ d: [String: Any], _ k: String) -> Bool { (d[k] as? Bool) ?? false }
        func date(_ d: [String: Any], _ k: String) -> Date? { text(d, k).flatMap(PeakDates.date) }

        let ordered = docs.sorted { (applyOrder.firstIndex(of: $0.kind) ?? 99) < (applyOrder.firstIndex(of: $1.kind) ?? 99) }
        for doc in ordered {
            let uuid = UUID(uuidString: doc.uuid)
            let key = doc.key

            if doc.deleted {
                if let uuid {
                    switch doc.kind {
                    case "exercise": if let m = exercises.removeValue(forKey: uuid) { context.delete(m) }
                    case "workout": if let m = workouts.removeValue(forKey: uuid) { context.delete(m) }
                    case "template": if let m = templates.removeValue(forKey: uuid) { context.delete(m) }
                    case "program": if let m = programs.removeValue(forKey: uuid) { context.delete(m) }
                    case "record": if let m = records.removeValue(forKey: uuid) { context.delete(m) }
                    case "set": if let m = sets.removeValue(forKey: uuid) { context.delete(m) }
                    case "bodyweight": if let m = weights.removeValue(forKey: uuid) { context.delete(m) }
                    default: break
                    }
                }
                result.deleted.insert(key)
                continue
            }
            guard let d = doc.data else { result.skipped.insert(key); continue }

            switch doc.kind {
            case "setting":
                if applySetting(doc.uuid, d["value"], to: settings) { result.upserted.insert(key) } else { result.skipped.insert(key) }

            case "exercise":
                guard let uuid, let name = text(d, "name"), let created = date(d, "createdAt") else { result.skipped.insert(key); continue }
                let exercise = exercises[uuid] ?? {
                    let e = Exercise(name: name, muscleGroup: .petto)
                    e.uuid = uuid
                    context.insert(e)
                    exercises[uuid] = e
                    return e
                }()
                exercise.name = name
                exercise.muscleGroupRaw = MuscleGroup(rawValue: text(d, "muscleGroup") ?? "")?.rawValue ?? MuscleGroup.petto.rawValue
                exercise.notes = text(d, "notes") ?? ""
                exercise.incrementStep = number(d, "incrementStep") ?? 2.5
                exercise.sortIndex = integer(d, "sortIndex") ?? 0
                exercise.isArchived = flag(d, "isArchived")
                exercise.createdAt = created
                exercise.glyphRaw = MachineGlyph(rawValue: text(d, "glyph") ?? "")?.rawValue ?? ""
                exercise.photoOffset = number(d, "photoOffset") ?? 0
                exercise.isUnilateral = flag(d, "isUnilateral")
                exercise.restSeconds = integer(d, "restSeconds") ?? 0
                exercise.catalogID = text(d, "catalog") ?? ""
                exercise.isHidden = flag(d, "hidden")
                // i campi dei muscoli: un server vecchio non li manda, e allora restano quelli che ci sono
                if let muscles = d["muscles"] as? [String] { exercise.musclesRaw = muscles.joined(separator: ",") }
                if let view = text(d, "view") { exercise.viewRaw = view }
                if let custom = d["custom"] as? Bool { exercise.isCustom = custom }
                if let bar = number(d, "barWeight") { exercise.barWeight = bar }
                if let wd = text(d, "weightDisplay"), ["absolute", "relative"].contains(wd) { exercise.weightDisplay = wd }
                if let plate = d["plateLoaded"] as? Bool { exercise.plateLoaded = plate }
                if let side = d["perSide"] as? Bool { exercise.perSide = side }
                let sha = text(d, "photo") ?? ""
                if sha.isEmpty { exercise.photo = nil }
                else if let bytes = photos[sha] { exercise.photo = bytes }
                result.upserted.insert(key)

            case "workout":
                guard let uuid, let start = date(d, "date") else { result.skipped.insert(key); continue }
                let workout = workouts[uuid] ?? {
                    let w = Workout(date: start)
                    w.uuid = uuid
                    context.insert(w)
                    workouts[uuid] = w
                    return w
                }()
                workout.date = start
                workout.endedAt = date(d, "endedAt")
                workout.note = text(d, "note") ?? ""
                workout.name = text(d, "name") ?? ""
                workout.focusRaw = text(d, "focus") ?? ""
                workout.templateRaw = text(d, "template") ?? ""
                workout.plan = TemplateItems.parse(d["plan"])
                result.upserted.insert(key)

            case "template":
                guard let uuid, let name = text(d, "name"), let created = date(d, "createdAt") else { result.skipped.insert(key); continue }
                let template = templates[uuid] ?? {
                    let t = WorkoutTemplate(name: name)
                    t.uuid = uuid
                    context.insert(t)
                    templates[uuid] = t
                    return t
                }()
                template.name = name
                template.items = TemplateItems.parse(d["items"])
                template.sortIndex = integer(d, "sortIndex") ?? 0
                template.createdAt = created
                template.isHidden = flag(d, "hidden")
                template.iconRaw = text(d, "icon") ?? ""
                template.programRaw = text(d, "program")?.uppercased() ?? ""
                template.weekday = integer(d, "weekday") ?? -1
                result.upserted.insert(key)

            case "program":
                guard let uuid, let name = text(d, "name"), let created = date(d, "createdAt") else { result.skipped.insert(key); continue }
                let program = programs[uuid] ?? {
                    let p = WorkoutProgram(name: name)
                    p.uuid = uuid
                    context.insert(p)
                    programs[uuid] = p
                    return p
                }()
                program.name = name
                program.sortIndex = integer(d, "sortIndex") ?? 0
                program.createdAt = created
                program.useDays = flag(d, "useDays")
                result.upserted.insert(key)

            case "record":
                guard let uuid, let when = date(d, "date"), let weight = number(d, "weight"), let reps = integer(d, "reps"),
                      let exUUID = text(d, "exercise").flatMap(UUID.init(uuidString:)), let exercise = exercises[exUUID] else {
                    result.skipped.insert(key); continue
                }
                let record = records[uuid] ?? {
                    let r = MaxRecord(weight: weight, reps: reps, date: when)
                    r.uuid = uuid
                    context.insert(r)
                    records[uuid] = r
                    return r
                }()
                record.weight = weight
                record.reps = reps
                record.date = when
                record.isEstimated = false
                record.note = text(d, "note") ?? ""
                record.singleArm = flag(d, "singleArm")
                if record.exercise !== exercise { record.exercise = exercise }
                result.upserted.insert(key)

            case "set":
                guard let uuid, let created = date(d, "createdAt"), let weight = number(d, "weight"), let reps = integer(d, "reps"),
                      let wkUUID = text(d, "workout").flatMap(UUID.init(uuidString:)), let workout = workouts[wkUUID] else {
                    result.skipped.insert(key); continue
                }
                let set = sets[uuid] ?? {
                    let s = WorkoutSet(weight: weight, reps: reps)
                    s.uuid = uuid
                    context.insert(s)
                    sets[uuid] = s
                    return s
                }()
                set.weight = weight
                set.reps = reps
                set.rpe = number(d, "rpe")
                set.isWarmup = flag(d, "isWarmup")
                set.order = integer(d, "order") ?? 0
                set.createdAt = created
                set.excludedFromRecords = flag(d, "noRecord")
                set.singleArm = flag(d, "singleArm")
                if set.workout !== workout { set.workout = workout }
                let exercise = text(d, "exercise").flatMap(UUID.init(uuidString:)).flatMap { exercises[$0] }
                if set.exercise !== exercise { set.exercise = exercise }
                result.upserted.insert(key)

            case "bodyweight":
                guard let uuid, let when = date(d, "date"), let weight = number(d, "weight") else { result.skipped.insert(key); continue }
                let entry = weights[uuid] ?? {
                    let b = BodyWeightEntry(weight: weight, date: when)
                    b.uuid = uuid
                    context.insert(b)
                    weights[uuid] = b
                    return b
                }()
                entry.weight = weight
                entry.date = when
                entry.fromHealth = flag(d, "fromHealth")
                result.upserted.insert(key)

            default:
                result.skipped.insert(key)
            }
        }
        return result
    }
}
