import Foundation
import SwiftData

/// Una misurazione del massimale: peso × ripetizioni in una certa data.
/// Il 1RM non viene mai salvato, si ricalcola sempre (Epley).
@Model
final class MaxRecord {
    var uuid: UUID = UUID()
    var date: Date = Date()
    var weight: Double = 0
    var reps: Int = 1
    /// `true` quando il record nasce automaticamente da una serie di allenamento.
    var isEstimated: Bool = false
    var note: String = ""
    /// Massimale a un braccio: una linea a parte, non si confronta con quella a due braccia.
    var singleArm: Bool = false

    var exercise: Exercise?

    init(
        weight: Double,
        reps: Int = 1,
        date: Date = Date(),
        isEstimated: Bool = false,
        note: String = "",
        singleArm: Bool = false,
        exercise: Exercise? = nil
    ) {
        self.uuid = UUID()
        self.weight = weight
        self.reps = reps
        self.date = date
        self.isEstimated = isEstimated
        self.note = note
        self.singleArm = singleArm
        self.exercise = exercise
    }

    var estimatedOneRepMax: Double {
        OneRepMax.estimate(weight: weight, reps: reps)
    }
}
