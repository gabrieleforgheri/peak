import Foundation

extension Medals {
    /// Le medaglie com'erano a una certa data: serve al riepilogo per dire quali ha fatto salire un allenamento.
    /// Come `medalsAt` in web/src/lib/medalinfo.ts. Gli amici non cambiano con un allenamento: si passano da fuori.
    static func at(workouts: [Workout], exercises: [Exercise], friends: Int, until: Date, inclusive: Bool) -> [Medal] {
        func cut(_ d: Date) -> Bool { inclusive ? d <= until : d < until }
        let done = workouts.filter { !$0.isActive && cut($0.date) }
        let ids = Set(done.map(\.uuid))
        var records = 0, machines = 0
        var best = 0.0
        for e in exercises {
            let rs = e.twoArmRecords.filter { cut($0.date) }
            let ss = e.setList.filter { !$0.isWarmup && ($0.workout.map { ids.contains($0.uuid) } ?? false) }
            records += rs.count
            if !rs.isEmpty || !ss.isEmpty { machines += 1 }
            for r in rs { best = max(best, r.estimatedOneRepMax) }
            for s in ss where !s.singleArm { best = max(best, s.estimatedOneRepMax) }
        }
        return list(MedalInput(
            sessions: done.count, records: records,
            streak: Stats.weeklyStreak(workouts: done, now: until),
            volumeKg: done.reduce(0) { $0 + $1.totalVolume },
            bestOneRm: best, machines: machines, friends: friends
        ))
    }
}
