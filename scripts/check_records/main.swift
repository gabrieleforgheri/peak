// Esegue i vettori di prova condivisi (common/fixtures/records.json) sulla regola Swift dei
// record, senza simulatore né Xcode: la compila e la lancia scripts/check_records.sh.
import Foundation

struct Vector: Decodable {
    struct Manual: Decodable { let uuid: String; let exercise: String; let weight: Double; let reps: Int; let at: String }
    struct Set: Decodable {
        let uuid: String; let exercise: String?; let workout: String; let weight: Double; let reps: Int
        let order: Int; let at: String; let isWarmup: Bool?; let noRecord: Bool?
    }
    let name: String
    let workouts: [String]
    let manual: [Manual]
    let sets: [Set]
    let pr: [String]
}

func date(_ text: String) -> Date {
    // con e senza millisecondi: il formatter ISO8601 li vuole separati
    let withMs = ISO8601DateFormatter()
    withMs.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    let plain = ISO8601DateFormatter()
    plain.formatOptions = [.withInternetDateTime]
    return withMs.date(from: text) ?? plain.date(from: text)!
}

let path = CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : "common/fixtures/records.json"
let vectors = try JSONDecoder().decode([Vector].self, from: Data(contentsOf: URL(fileURLWithPath: path)))

var failed = 0
for vector in vectors {
    let live = Swift.Set(vector.workouts)
    var exercises = Swift.Set<String>()
    vector.manual.forEach { exercises.insert($0.exercise) }
    vector.sets.compactMap(\.exercise).forEach { exercises.insert($0) }

    var got: [String] = []
    for exercise in exercises {
        let manual = vector.manual.filter { $0.exercise == exercise }
            .map { RecordRules.ManualRecord(uuid: $0.uuid, date: date($0.at), weight: $0.weight, reps: $0.reps) }
        let sets = vector.sets.filter { $0.exercise == exercise && live.contains($0.workout) }
            .map { RecordRules.SetEntry(uuid: $0.uuid, createdAt: date($0.at), order: $0.order, weight: $0.weight, reps: $0.reps,
                                        isWarmup: $0.isWarmup ?? false, excludedFromRecords: $0.noRecord ?? false) }
        got += RecordRules.personalRecordSets(manual: manual, sets: sets)
    }
    if got.sorted() == vector.pr.sorted() {
        print("  ok  \(vector.name)")
    } else {
        failed += 1
        print("FAIL  \(vector.name): attesi \(vector.pr.sorted()), ottenuti \(got.sorted())")
    }
}
print(failed == 0 ? "\n\(vectors.count) vettori passati." : "\n\(failed) vettori falliti.")
exit(failed == 0 ? 0 : 1)
