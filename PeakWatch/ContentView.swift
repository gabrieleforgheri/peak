import SwiftUI
import WatchKit

/// Come l'app Allenamento: scheda di partenza, conto alla rovescia, pagine da scorrere, riepilogo.
struct ContentView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        Group {
            switch model.workout.phase {
            case .idle: StartView()
            case .countdown: CountdownView()
            case .active: WorkoutTabs()
            case .summary: SummaryView()
            }
        }
        .background(.black)
    }
}

/// Pagine orizzontali: comandi · numeri · serie · musica.
private struct WorkoutTabs: View {
    @Environment(WatchModel.self) private var model
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced

    var body: some View {
        @Bindable var model = model
        TabView(selection: $model.page) {
            ControlsPage().tag(WatchModel.Page.controls)
            MetricsPage().tag(WatchModel.Page.metrics)
            LogPage().tag(WatchModel.Page.log)
            NowPlayingView().tag(WatchModel.Page.music)
        }
        .tabViewStyle(.page(indexDisplayMode: isLuminanceReduced ? .never : .automatic))
    }
}

/// Nessun allenamento in corso: la scheda grande di Apple. Il tocco avvia la sessione sul polso;
/// la serie si apre poi dall'iPhone.
private struct StartView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { model.startWorkout() } label: {
                HStack(spacing: 10) {
                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.title3)
                        .foregroundStyle(.black)
                        .frame(width: 40, height: 40)
                        .background(Color.workoutEnergy, in: Circle())
                    Text(w("Allenamento di forza", "Strength Training"))
                        .font(.headline)
                        .multilineTextAlignment(.leading)
                        .lineLimit(2)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.vertical, 10)
                .padding(.horizontal, 10)
                .background(Color.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            }
            .buttonStyle(.plain)

            Text(model.exercise.map { w("Sull'iPhone: \($0.name)", "On iPhone: \($0.name)") }
                 ?? w("Poi apri un esercizio nella sessione sull'iPhone.", "Then open an exercise in the session on your iPhone."))
                .font(.caption2)
                .foregroundStyle(.secondary)
                .lineLimit(3)
                .padding(.horizontal, 4)
        }
        .padding(.horizontal, 4)
    }
}

/// 3, 2, 1 prima di partire.
private struct CountdownView: View {
    @Environment(WatchModel.self) private var model

    var body: some View {
        Text("\(model.workout.countdown)")
            .font(.system(size: 96, weight: .bold, design: .rounded))
            .foregroundStyle(Color.workoutTime)
            .contentTransition(.numericText(countsDown: true))
            .animation(.snappy, value: model.workout.countdown)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
