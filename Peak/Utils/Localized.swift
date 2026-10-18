import SwiftUI

// Ogni testo che arriva come `String` (letterale, interpolato o variabile) passa da `Lang.tr`: lo stesso elenco di traduzioni
// (`en.lproj/Localizable.strings`) vale per tutti. Questi inizializzatori, più specifici di quelli di SwiftUI, vincono sempre.

extension Text {
    init(_ content: String) { self.init(verbatim: content.tr) }
}

extension Label where Title == Text, Icon == Image {
    init(_ title: String, systemImage name: String) {
        self.init { Text(verbatim: title.tr) } icon: { Image(systemName: name) }
    }
}

extension Button where Label == Text {
    init(_ title: String, role: ButtonRole? = nil, action: @escaping () -> Void) {
        self.init(role: role, action: action) { Text(verbatim: title.tr) }
    }
}

extension Toggle where Label == Text {
    init(_ title: String, isOn: Binding<Bool>) {
        self.init(isOn: isOn) { Text(verbatim: title.tr) }
    }
}

extension Picker where Label == Text {
    init(_ title: String, selection: Binding<SelectionValue>, @ViewBuilder content: () -> Content) {
        self.init(selection: selection, content: content) { Text(verbatim: title.tr) }
    }
}

extension TextField where Label == Text {
    init(_ title: String, text: Binding<String>, axis: Axis) {
        self.init(text: text, axis: axis) { Text(verbatim: title.tr) }
    }
    init(_ title: String, text: Binding<String>) {
        self.init(text: text) { Text(verbatim: title.tr) }
    }
}

extension View {
    func navigationTitle(_ title: String) -> some View { navigationTitle(Text(verbatim: title.tr)) }

    func accessibilityLabel(_ label: String) -> some View { accessibilityLabel(Text(verbatim: label.tr)) }

    func searchable(text: Binding<String>, placement: SearchFieldPlacement = .automatic, prompt: String) -> some View {
        searchable(text: text, placement: placement, prompt: Text(verbatim: prompt.tr))
    }

    func alert<A: View, M: View>(_ title: String, isPresented: Binding<Bool>, @ViewBuilder actions: () -> A, @ViewBuilder message: () -> M) -> some View {
        alert(Text(verbatim: title.tr), isPresented: isPresented, actions: actions, message: message)
    }

    func alert<A: View>(_ title: String, isPresented: Binding<Bool>, @ViewBuilder actions: () -> A) -> some View {
        alert(Text(verbatim: title.tr), isPresented: isPresented, actions: actions)
    }

    func confirmationDialog<A: View, M: View>(_ title: String, isPresented: Binding<Bool>, titleVisibility: Visibility = .automatic, @ViewBuilder actions: () -> A, @ViewBuilder message: () -> M) -> some View {
        confirmationDialog(Text(verbatim: title.tr), isPresented: isPresented, titleVisibility: titleVisibility, actions: actions, message: message)
    }

    func confirmationDialog<A: View>(_ title: String, isPresented: Binding<Bool>, titleVisibility: Visibility = .automatic, @ViewBuilder actions: () -> A) -> some View {
        confirmationDialog(Text(verbatim: title.tr), isPresented: isPresented, titleVisibility: titleVisibility, actions: actions)
    }
}
