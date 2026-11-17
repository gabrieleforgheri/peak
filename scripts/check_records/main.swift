// Esegue i vettori di prova condivisi sulle regole Swift, senza simulatore né Xcode:
// common/fixtures/records.json (record) e common/fixtures/progression.json (doppia progressione).
// La compila e la lancia scripts/check_records.sh.
import Foundation

struct Vector: Decodable {
    struct Manual: Decodable { let uuid: String; let exercise: String; let weight: Double; let reps: Int; let at: String; let singleArm: Bool? }
    struct Set: Decodable {
        let uuid: String; let exercise: String?; let workout: String; let weight: Double; let reps: Int
        let order: Int; let at: String; let isWarmup: Bool?; let noRecord: Bool?; let singleArm: Bool?
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
            .map { RecordRules.ManualRecord(uuid: $0.uuid, date: date($0.at), weight: $0.weight, reps: $0.reps, singleArm: $0.singleArm ?? false) }
        let sets = vector.sets.filter { $0.exercise == exercise && live.contains($0.workout) }
            .map { RecordRules.SetEntry(uuid: $0.uuid, createdAt: date($0.at), order: $0.order, weight: $0.weight, reps: $0.reps,
                                        isWarmup: $0.isWarmup ?? false, excludedFromRecords: $0.noRecord ?? false, singleArm: $0.singleArm ?? false) }
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

// Doppia progressione: stesso file di common/domain.test.ts.
struct ProgressionVector: Decodable {
    struct Entry: Decodable { let weight: Double; let reps: Int }
    struct Expected: Decodable { let weight: Double; let reps: Int; let up: Bool }
    let name: String
    let lastSession: [Entry]
    let step: Double
    let range: [Int]
    let expected: Expected?
}

let progressionPath = CommandLine.arguments.count > 2 ? CommandLine.arguments[2] : "common/fixtures/progression.json"
let progressionVectors = try JSONDecoder().decode([ProgressionVector].self, from: Data(contentsOf: URL(fileURLWithPath: progressionPath)))
var progressionFailed = 0
for vector in progressionVectors {
    let got = Progression.next(lastSession: vector.lastSession.map { (weight: $0.weight, reps: $0.reps) },
                               step: vector.step, range: vector.range[0]...vector.range[1])
    let want = vector.expected.map { Progression.Suggestion(weight: $0.weight, reps: $0.reps, up: $0.up) }
    if got == want {
        print("  ok  progressione: \(vector.name)")
    } else {
        progressionFailed += 1
        print("FAIL  progressione: \(vector.name): attesi \(String(describing: want)), ottenuti \(String(describing: got))")
    }
}
print(progressionFailed == 0 ? "\n\(progressionVectors.count) vettori di progressione passati." : "\n\(progressionFailed) vettori di progressione falliti.")
exit(failed + progressionFailed == 0 ? 0 : 1)
