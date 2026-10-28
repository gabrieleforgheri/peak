import SwiftUI

/// «2 br | 1 br» (gambe «2 gb | 1 gb»): una pillola piccola accanto al titolo del peso. `singleArm` è il flag della serie
/// o del massimale; il core non la mostra mai (vedi `MuscleGroup.hasSides`).
struct SideSegment: View {
    @Binding var singleArm: Bool
    let group: MuscleGroup

    var body: some View {
        HStack(spacing: 0) {
            option(false, label: group.side(.two))
            option(true, label: group.side(.one))
        }
        .padding(2)
        .background(Theme.cardStrong, in: RoundedRectangle(cornerRadius: 9, style: .continuous))
        .accessibilityElement(children: .contain)
        .accessibilityLabel(group.side(.long))
    }

    private func option(_ value: Bool, label: String) -> some View {
        let selected = singleArm == value
        return Button {
            guard singleArm != value else { return }
            singleArm = value
            Haptics.tap()
        } label: {
            Text(label)
                .font(Theme.rounded(12, .bold))
                .foregroundStyle(selected ? Theme.textPrimary : Theme.textTertiary)
                .padding(.vertical, 4)
                .padding(.horizontal, 9)
                .background(selected ? Color.white.opacity(0.16) : .clear, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }
}
