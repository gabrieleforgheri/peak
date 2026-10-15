import Foundation

/// Stima del 1RM (Epley) partendo da peso × ripetizioni.
///
/// È solo una **nota a margine**: il massimale dell'app è il carico vero sollevato
/// sul macchinario (vedi `MaxRanking`), questa stima serve a confrontare serie con
/// ripetizioni diverse e a ordinare le classifiche, e non decide mai un record.
enum OneRepMax {
    /// Massimale stimato in kg. Con 1 ripetizione restituisce il peso grezzo.
    static func estimate(weight: Double, reps: Int) -> Double {
        guard weight > 0, reps >= 1 else { return 0 }
        if reps == 1 { return weight }
        return weight * (1 + Double(reps) / 30)
    }
}
