import Foundation

/// Quali serie sono un nuovo massimale. È la stessa regola di `common/records.ts` (Peak sul
/// web e sul server), portata riga per riga: i due devono dare lo stesso risultato, e lo
/// controllano gli stessi vettori (`common/fixtures/records.json`, `scripts/check_records`).
///
/// I massimali "da allenamento" non si salvano come fatti a sé: si ricalcolano dalle serie.
/// Così cancellare o correggere una serie, o un intero allenamento, lascia sempre i record
/// giusti, e due dispositivi non possono mai litigare su un record.
enum RecordRules {

    struct ManualRecord {
        let uuid: String
        let date: Date
        let weight: Double
        let reps: Int
        var singleArm = false
    }

    struct SetEntry {
        let uuid: String
        let createdAt: Date
        let order: Int
        let weight: Double
        let reps: Int
        let isWarmup: Bool
        let excludedFromRecords: Bool
        var singleArm = false
    }

    private struct Event {
        let time: Int64
        let isManual: Bool
        let order: Int
        let uuid: String
        let weight: Double
        let reps: Int
    }

    /// Secondi interi, come nei backup e sul server: due eventi nello stesso secondo
    /// si ordinano per regola (il manuale prima), non per i millisecondi.
    static func floorSeconds(_ date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970.rounded(.down))
    }

    /// Gli uuid delle serie che battono il migliore fino a quel momento, in ordine di tempo.
    /// Si percorrono i massimali inseriti a mano e le serie valide (non riscaldamento, non
    /// escluse, peso e ripetizioni sensati): un manuale che batte il migliore lo sostituisce,
    /// una serie che lo batte è un record. `sets` va già filtrato a quelle di un allenamento
    /// che esiste ancora.
    static func personalRecordSets(manual: [ManualRecord], sets: [SetEntry]) -> [String] {
        var records: [String] = []
        // Due linee separate: il peso a un braccio (sul singolo manico) non si confronta con quello a due braccia.
        for singleArm in [false, true] {
            var events = manual.filter { $0.singleArm == singleArm }.map {
                Event(time: floorSeconds($0.date), isManual: true, order: 0, uuid: $0.uuid, weight: $0.weight, reps: $0.reps)
            }
            for set in sets where set.singleArm == singleArm && !set.isWarmup && !set.excludedFromRecords && set.weight > 0 && set.reps >= 1 {
                events.append(Event(time: floorSeconds(set.createdAt), isManual: false, order: set.order,
                                    uuid: set.uuid, weight: set.weight, reps: set.reps))
            }
            events.sort { a, b in
                if a.time != b.time { return a.time < b.time }
                if a.isManual != b.isManual { return a.isManual }
                if a.order != b.order { return a.order < b.order }
                return a.uuid < b.uuid
            }

            var best: (weight: Double, reps: Int)?
            for event in events where MaxRanking.beats(weight: event.weight, reps: event.reps, than: best) {
                best = (event.weight, event.reps)
                if !event.isManual { records.append(event.uuid) }
            }
        }
        return records
    }
}
