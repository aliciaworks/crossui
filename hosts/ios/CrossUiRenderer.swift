import SwiftUI
import UIKit

protocol CrossUiDocumentProvider {
    func initialDocument() throws -> String
    func dispatchEvent(_ event: String) throws -> String
}

// MARK: - Document model

struct CrossUiDocument: Decodable {
    let version: Int
    let root: CrossUiNode
    let theme: CrossUiTheme

    static let supportedVersion = 2

    static func decode(_ json: String) throws -> CrossUiDocument {
        try CrossUiUpdate.decode(json).document
    }
}

struct CrossUiTheme: Decodable {
    let colorScheme: String?
    let tokens: [String: CrossUiToken]

    init(colorScheme: String? = nil, tokens: [String: CrossUiToken] = [:]) {
        self.colorScheme = colorScheme
        self.tokens = tokens
    }

    var primary: Color? {
        guard case let .color(value)? = tokens["primary"] else { return nil }
        return Color(crossUiHex: value)
    }

    var error: Color? {
        guard case let .color(value)? = tokens["error"] else { return nil }
        return Color(crossUiHex: value)
    }

    var preferredColorScheme: ColorScheme? {
        switch colorScheme {
        case "light": .light
        case "dark": .dark
        default: nil
        }
    }

    enum CodingKeys: String, CodingKey {
        case colorScheme = "color_scheme"
        case tokens
    }
}

enum CrossUiToken: Decodable {
    case color(String)
    case number(Double)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(String.self) { self = .color(value) }
        else { self = .number(try container.decode(Double.self)) }
    }
}

private extension Color {
    init?(crossUiHex value: String) {
        let hex = value.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        guard hex.count == 6, let rgb = UInt64(hex, radix: 16) else { return nil }
        self.init(
            red: Double((rgb >> 16) & 0xff) / 255,
            green: Double((rgb >> 8) & 0xff) / 255,
            blue: Double(rgb & 0xff) / 255
        )
    }
}

struct CrossUiEffect {
    let type: String
    let message: String?
}

indirect enum CrossUiJsonValue: Decodable {
    case object([String: CrossUiJsonValue])
    case array([CrossUiJsonValue])
    case string(String)
    case number(Double)
    case boolean(Bool)
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(Bool.self) { self = .boolean(value) }
        else if let value = try? container.decode(Double.self) { self = .number(value) }
        else if let value = try? container.decode(String.self) { self = .string(value) }
        else if let value = try? container.decode([String: CrossUiJsonValue].self) { self = .object(value) }
        else { self = .array(try container.decode([CrossUiJsonValue].self)) }
    }
}

struct CrossUiUpdate {
    let document: CrossUiDocument
    let effects: [CrossUiEffect]

    static func decode(_ json: String) throws -> CrossUiUpdate {
        let data = Data(json.utf8)
        let response = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        if let update = response?["document"] {
            let documentData = try JSONSerialization.data(withJSONObject: update)
            let document = try JSONDecoder().decode(CrossUiDocument.self, from: documentData)
            guard document.version == CrossUiDocument.supportedVersion else { throw CrossUiDocumentError.unsupportedVersion(document.version) }
            // Walk raw JSON tree to populate extensions on decoded nodes.
            if let rootRaw = update as? [String: Any] {
                populateExtensions(node: document.root, raw: rootRaw)
            }
            let effects = (response?["effects"] as? [[String: Any]] ?? []).map { CrossUiEffect(type: $0["type"] as? String ?? "", message: $0["message"] as? String) }
            return CrossUiUpdate(document: document, effects: effects)
        }
        let document = try JSONDecoder().decode(CrossUiDocument.self, from: data)
        guard document.version == CrossUiDocument.supportedVersion else { throw CrossUiDocumentError.unsupportedVersion(document.version) }
        // Walk raw JSON root to populate extensions.
        if let rootRaw = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
            populateExtensions(node: document.root, raw: rootRaw)
        }
        return CrossUiUpdate(document: document, effects: [])
    }

    /// Recursively walk the raw JSON and decoded node tree in parallel,
    /// extracting ``extensions`` arrays and assigning them to the matching node.
    private static func populateExtensions(node: CrossUiNode, raw: [String: Any]) {
        if let rawExts = raw["extensions"] as? [[String: Any]] {
            node.extensions = CrossUiExtension.parseList(rawExts)
        }
        guard let rawChildren = raw["children"] as? [[String: Any]],
              node.children.count == rawChildren.count else { return }
        for (child, rawChild) in zip(node.children, rawChildren) {
            populateExtensions(node: child, raw: rawChild)
        }
    }
}

private enum CrossUiDocumentError: Error { case unsupportedVersion(Int) }

// MARK: - Platform extensions (parsed from raw JSON)

struct CrossUiExtension {
    let platform: String
    let type: String
    let fields: [String: Any]

    static func parseList(_ raw: [[String: Any]]) -> [CrossUiExtension] {
        raw.compactMap { dict in
            guard let platform = dict["platform"] as? String,
                  let data = dict["data"] as? [String: Any],
                  let type = data["type"] as? String else { return nil }
            return CrossUiExtension(platform: platform, type: type, fields: data)
        }
    }
}

// MARK: - CrossUiNode (expanded for v2.2)

class CrossUiNode: Decodable, Identifiable {
    let key: String
    let type: String
    let text: String?
    let style: String?
    let title: String?
    let label: String?
    let value: String?
    let action: String?
    let variant: String?
    let onChange: String?
    let onSelect: String?
    let active: String?
    let axis: String?
    let alignment: String?
    let spacing: String?
    let platform: String?
    let name: String?
    let payload: CrossUiJsonValue?
    let placeholder: String?
    let secure: Bool
    let inputType: String?
    let returnKey: String?
    let src: String?
    let alt: String?
    let checked: Bool
    let confirmLabel: String?
    let confirmAction: String?
    let cancelLabel: String?
    let cancelAction: String?
    let mode: String?
    let respectSafeArea: Bool
    let min: Double?
    let max: Double?
    let step: Double?
    let options: [CrossUiPickerOption]?
    let selected: String?
    let dateMode: String?
    let semantics: CrossUiSemantics
    let enabled: Bool
    let children: [CrossUiNode]
    var extensions: [CrossUiExtension]

    var id: String { key }

    // Explicit memberwise init required for class (struct gets it automatically).
    init(key: String, type: String, text: String?, style: String?, title: String?,
         label: String?, value: String?, action: String?, variant: String?,
         onChange: String?, onSelect: String?, active: String?, axis: String?,
         alignment: String?, spacing: String?, platform: String?, name: String?,
         payload: CrossUiJsonValue?, placeholder: String?, secure: Bool,
         inputType: String?, returnKey: String?, src: String?, alt: String?,
         checked: Bool, confirmLabel: String?, confirmAction: String?,
         cancelLabel: String?, cancelAction: String?, mode: String?,
         respectSafeArea: Bool, min: Double?, max: Double?, step: Double?,
         options: [CrossUiPickerOption]?, selected: String?, dateMode: String?,
         semantics: CrossUiSemantics, enabled: Bool, children: [CrossUiNode],
         extensions: [CrossUiExtension] = []) {
        self.key = key; self.type = type; self.text = text; self.style = style
        self.title = title; self.label = label; self.value = value
        self.action = action; self.variant = variant; self.onChange = onChange
        self.onSelect = onSelect; self.active = active; self.axis = axis
        self.alignment = alignment; self.spacing = spacing; self.platform = platform
        self.name = name; self.payload = payload; self.placeholder = placeholder
        self.secure = secure; self.inputType = inputType; self.returnKey = returnKey
        self.src = src; self.alt = alt; self.checked = checked
        self.confirmLabel = confirmLabel; self.confirmAction = confirmAction
        self.cancelLabel = cancelLabel; self.cancelAction = cancelAction
        self.mode = mode; self.respectSafeArea = respectSafeArea
        self.min = min; self.max = max; self.step = step
        self.options = options; self.selected = selected; self.dateMode = dateMode
        self.semantics = semantics; self.enabled = enabled
        self.children = children; self.extensions = extensions
    }

    enum CodingKeys: String, CodingKey {
        case key, type, text, style, title, label, value, action, variant, active, children, semantics, placeholder, secure, axis, alignment, spacing, platform, name, payload, src, alt, checked, mode, min, max, step, options, selected
        case onChange = "on_change"
        case onSelect = "on_select"
        case inputType = "input_type"
        case returnKey = "return_key"
        case confirmLabel = "confirm_label"
        case confirmAction = "confirm_action"
        case cancelLabel = "cancel_label"
        case cancelAction = "cancel_action"
        case respectSafeArea = "respect_safe_area"
        case dateMode = "mode"
    }

    required init(from decoder: Decoder) throws {
        let vals = try decoder.container(keyedBy: CodingKeys.self)
        key = try vals.decode(String.self, forKey: .key)
        type = try vals.decode(String.self, forKey: .type)
        text = try vals.decodeIfPresent(String.self, forKey: .text)
        style = try vals.decodeIfPresent(String.self, forKey: .style)
        title = try vals.decodeIfPresent(String.self, forKey: .title)
        label = try vals.decodeIfPresent(String.self, forKey: .label)
        value = try vals.decodeIfPresent(String.self, forKey: .value)
        action = try vals.decodeIfPresent(String.self, forKey: .action)
        variant = try vals.decodeIfPresent(String.self, forKey: .variant)
        onChange = try vals.decodeIfPresent(String.self, forKey: .onChange)
        onSelect = try vals.decodeIfPresent(String.self, forKey: .onSelect)
        active = try vals.decodeIfPresent(String.self, forKey: .active)
        axis = try vals.decodeIfPresent(String.self, forKey: .axis)
        alignment = try vals.decodeIfPresent(String.self, forKey: .alignment)
        spacing = try vals.decodeIfPresent(String.self, forKey: .spacing)
        platform = try vals.decodeIfPresent(String.self, forKey: .platform)
        name = try vals.decodeIfPresent(String.self, forKey: .name)
        payload = try vals.decodeIfPresent(CrossUiJsonValue.self, forKey: .payload)
        placeholder = try vals.decodeIfPresent(String.self, forKey: .placeholder)
        secure = try vals.decodeIfPresent(Bool.self, forKey: .secure) ?? false
        inputType = try vals.decodeIfPresent(String.self, forKey: .inputType)
        returnKey = try vals.decodeIfPresent(String.self, forKey: .returnKey)
        src = try vals.decodeIfPresent(String.self, forKey: .src)
        alt = try vals.decodeIfPresent(String.self, forKey: .alt)
        checked = try vals.decodeIfPresent(Bool.self, forKey: .checked) ?? false
        confirmLabel = try vals.decodeIfPresent(String.self, forKey: .confirmLabel)
        confirmAction = try vals.decodeIfPresent(String.self, forKey: .confirmAction)
        cancelLabel = try vals.decodeIfPresent(String.self, forKey: .cancelLabel)
        cancelAction = try vals.decodeIfPresent(String.self, forKey: .cancelAction)
        mode = try vals.decodeIfPresent(String.self, forKey: .mode)
        respectSafeArea = try vals.decodeIfPresent(Bool.self, forKey: .respectSafeArea) ?? true
        min = try vals.decodeIfPresent(Double.self, forKey: .min)
        max = try vals.decodeIfPresent(Double.self, forKey: .max)
        step = try vals.decodeIfPresent(Double.self, forKey: .step)
        options = try vals.decodeIfPresent([CrossUiPickerOption].self, forKey: .options)
        selected = try vals.decodeIfPresent(String.self, forKey: .selected)
        dateMode = try vals.decodeIfPresent(String.self, forKey: .dateMode)
        children = try vals.decodeIfPresent([CrossUiNode].self, forKey: .children) ?? []
        semantics = try vals.decodeIfPresent(CrossUiSemantics.self, forKey: .semantics) ?? CrossUiSemantics()
        enabled = semantics.enabled ?? true
        extensions = []  // filled later from raw JSON during CrossUiUpdate.decode
    }
}

struct CrossUiPickerOption: Decodable {
    let label: String
    let value: String
}

private struct CrossUiSemantics: Decodable {
    let label: String?; let hint: String?; let role: String?; let enabled: Bool?
    init(label: String? = nil, hint: String? = nil, role: String? = nil, enabled: Bool? = nil) {
        self.label = label; self.hint = hint; self.role = role; self.enabled = enabled
    }
}

// MARK: - Host

struct CrossUiHost: View {
    let provider: any CrossUiDocumentProvider
    let platformView: (CrossUiNode) -> AnyView?
    @State private var document: CrossUiDocument
    @State private var notification: String?
    @State private var presentedDialog: CrossUiNode?

    init(provider: any CrossUiDocumentProvider, platformView: @escaping (CrossUiNode) -> AnyView? = { _ in nil }) {
        self.provider = provider
        self.platformView = platformView
        _document = State(initialValue: (try? provider.initialDocument()).flatMap { try? CrossUiDocument.decode($0) } ?? CrossUiDocument(version: CrossUiDocument.supportedVersion, root: CrossUiNode.empty, theme: CrossUiTheme()))
    }

    var body: some View {
        CrossUiRenderer(
            node: document.root,
            theme: document.theme,
            platformView: platformView,
            dispatch: dispatch,
            presentDialog: { presentedDialog = $0 }
        )
        .preferredColorScheme(document.theme.preferredColorScheme)
        .alert("CrossUI", isPresented: Binding(get: { notification != nil }, set: { if !$0 { notification = nil } })) {
            Button("OK", role: .cancel) { notification = nil }
        } message: { Text(notification ?? "") }
        .alert(presentedDialog?.title ?? "", isPresented: Binding(
            get: { presentedDialog != nil },
            set: { if !$0 || presentedDialog?.type == "dialog" && presentedDialog?.cancelAction == nil && presentedDialog?.confirmAction == nil { presentedDialog = nil } }
        )) {
            if let dialog = presentedDialog {
                if let cancelLabel = dialog.cancelLabel, let cancelAction = dialog.cancelAction {
                    Button(cancelLabel, role: .cancel) { dispatch(CrossUiEvent.pressed(dialog.key, cancelAction)) }
                }
                if let confirmLabel = dialog.confirmLabel, let confirmAction = dialog.confirmAction {
                    Button(confirmLabel) { dispatch(CrossUiEvent.pressed(dialog.key, confirmAction)); presentedDialog = nil }
                }
            }
        } message: {
            if let body = presentedDialog?.children.first, body.type == "text" { Text(body.text ?? "") }
        }
    }

    private func dispatch(_ event: String) {
        // Trigger haptic feedback before dispatching if the node has the extension.
        triggerHaptic(for: event)
        guard let json = try? provider.dispatchEvent(event), let update = try? CrossUiUpdate.decode(json) else { return }
        document = update.document
        notification = update.effects.first(where: { $0.type == "notification" })?.message
    }

    private func triggerHaptic(for event: String) {
        guard let data = event.data(using: .utf8),
              let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let nodeKey = payload["node_key"] as? String else { return }
        let node = findNode(key: nodeKey, in: document.root)
        guard let haptic = node?.iosExtension("haptic_feedback"),
              let feedbackType = haptic.fields["feedback_type"] as? String else { return }
        let style: UIImpactFeedbackGenerator.FeedbackStyle = switch feedbackType {
            case "light": .light; case "medium": .medium; case "heavy": .heavy
            case "success", "warning", "error": .medium
            default: .light
        }
        let generator = UIImpactFeedbackGenerator(style: style)
        generator.impactOccurred()
    }

    private func findNode(key: String, in node: CrossUiNode) -> CrossUiNode? {
        if node.key == key { return node }
        for child in node.children {
            if let found = findNode(key: key, in: child) { return found }
        }
        return nil
    }
}

private extension CrossUiNode {
    static let empty = CrossUiNode(key: "empty", type: "text", text: "Unable to load CrossUI document",
        style: nil, title: nil, label: nil, value: nil, action: nil, variant: nil,
        onChange: nil, onSelect: nil, active: nil, axis: nil, alignment: nil,
        spacing: nil, platform: nil, name: nil, payload: nil, placeholder: nil,
        secure: false, inputType: nil, returnKey: nil, src: nil, alt: nil, checked: false,
        confirmLabel: nil, confirmAction: nil, cancelLabel: nil, cancelAction: nil,
        mode: nil, respectSafeArea: true, min: nil, max: nil, step: nil, options: nil,
        selected: nil, dateMode: nil, semantics: CrossUiSemantics(), enabled: true,
        children: [], extensions: [])

    /// Returns the first iOS extension of a given type, if present.
    func iosExtension(_ type: String) -> CrossUiExtension? {
        extensions.first { $0.platform == "ios" && $0.type == type }
    }
}

// MARK: - Renderer

struct CrossUiRenderer: View {
    let node: CrossUiNode
    let theme: CrossUiTheme
    let platformView: (CrossUiNode) -> AnyView?
    let dispatch: (String) -> Void
    var presentDialog: ((CrossUiNode) -> Void)? = nil

    var body: some View {
        switch node.type {
        case "navigation": navigationView
        case "route": routeView
        case "stack": stackView
        case "form": VStack(alignment: .leading, spacing: 12) { children }
        case "list": VStack(alignment: .leading, spacing: 8) { ForEach(node.children) { listItem($0) } }
        case "loading": HStack { ProgressView(); if let label = node.label { Text(label) } }
        case "text": textView
        case "input": inputView
        case "button": buttonView
        case "toggle": toggleView
        case "image": imageView
        case "slider": sliderView
        case "picker": pickerView
        case "date_picker": datePickerView
        case "dialog": EmptyView()
        case "checkbox": checkboxView
        case "divider": Divider()
        case "card": cardView
        case "chip": chipView
        case "platform_view": if node.platform == "ios", let view = platformView(node) { view } else { Text("Unsupported platform view: \(node.key)") }
        default: Text("Unsupported CrossUI node: \(node.type)")
        }
    }

    @ViewBuilder private var children: some View {
        ForEach(node.children) { CrossUiRenderer(node: $0, theme: theme, platformView: platformView, dispatch: dispatch, presentDialog: presentDialog) }
    }

    // MARK: Navigation

    @ViewBuilder private var navigationView: some View {
        if node.mode == "stack" {
            // Push-stack: render only the active route.
            if let route = node.children.first(where: { $0.key == node.active }) {
                CrossUiRenderer(node: route, theme: theme, platformView: platformView, dispatch: dispatch, presentDialog: presentDialog)
            }
        } else {
            // Tab bar: all routes as tab items.
            TabView(selection: Binding(get: { node.active }, set: { _ in })) {
                ForEach(node.children) { route in
                    CrossUiRenderer(node: route, theme: theme, platformView: platformView, dispatch: dispatch, presentDialog: presentDialog)
                        .tabItem { Text(route.title ?? route.key) }
                        .tag(route.key)
                }
            }
        }
    }

    // MARK: Route

    @ViewBuilder private var routeView: some View {
        let content = ScrollView { children }
        let base: some View = if node.respectSafeArea {
            NavigationStack { content.navigationTitle(node.title ?? "") }
        } else {
            NavigationStack { content.navigationTitle(node.title ?? "").ignoresSafeArea() }
        }
        // Platform extension: presentation_style
        let style = node.iosExtension("presentation_style")?.fields["style"] as? String
        switch style {
        case "sheet":
            AnyView(base.sheet(isPresented: .constant(true)) { content })
        case "full_screen_cover":
            AnyView(base.fullScreenCover(isPresented: .constant(true)) { content })
        default:
            AnyView(base)
        }
    }

    // MARK: Stack

    @ViewBuilder private var stackView: some View {
        if node.axis == "horizontal" {
            HStack(alignment: hAlign, spacing: resolvedSpacing) { children }
        } else {
            VStack(alignment: vAlign, spacing: resolvedSpacing) { children }
        }
    }

    private var resolvedSpacing: CGFloat {
        switch node.spacing { case "spacing.sm": 8; case "spacing.lg": 24; default: 16 }
    }
    private var hAlign: VerticalAlignment {
        switch node.alignment { case "start": .top; case "end": .bottom; default: .center }
    }
    private var vAlign: HorizontalAlignment {
        switch node.alignment { case "center": .center; case "end": .trailing; default: .leading }
    }

    // MARK: Text

    @ViewBuilder private var textView: some View {
        let font: Font = switch node.style {
            case "display": .largeTitle
            case "headline": .title
            case "title": .title2
            case "caption": .caption
            case "footnote": .footnote
            default: .body
        }
        Text(node.text ?? "")
            .font(font)
            .accessibilityAddTraits(node.semantics.role == "header" ? .isHeader : [])
            .accessibilityLabel(node.semantics.label ?? node.text ?? node.key)
    }

    // MARK: Input

    @ViewBuilder private var inputView: some View {
        let binding = Binding(get: { node.value ?? "" }, set: { dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", $0)) })
        let base: AnyView = if node.secure {
            AnyView(SecureField(node.placeholder ?? node.label ?? "", text: binding))
        } else {
            AnyView(TextField(node.placeholder ?? node.label ?? "", text: binding))
        }
        base
            .keyboardType(uiKeyboardType)
            .submitLabel(submitLabel)
            .disabled(!node.enabled)
            .accessibilityLabel(node.semantics.label ?? node.label ?? node.key)
            .accessibilityHint(node.semantics.hint ?? "")
            .apply {
                if node.inputType == "email" { $0.textContentType(.emailAddress).autocapitalization(.none) }
                else if node.inputType == "password" || node.secure { $0.textContentType(.password) }
                else if node.inputType == "phone" { $0.textContentType(.telephoneNumber) }
                else if node.inputType == "url" { $0.textContentType(.URL).autocapitalization(.none) }
            }
    }

    private var uiKeyboardType: UIKeyboardType {
        switch node.inputType {
        case "email": .emailAddress
        case "number": .decimalPad
        case "phone": .phonePad
        case "url": .URL
        default: .default
        }
    }
    private var submitLabel: SubmitLabel {
        switch node.returnKey {
        case "go": .go; case "search": .search; case "send": .send; case "next": .next
        default: .done
        }
    }

    // MARK: Button

    @ViewBuilder private var buttonView: some View {
        Group {
            if node.variant == "destructive" {
                Button(node.label ?? "", role: .destructive) { dispatch(CrossUiEvent.pressed(node.key, node.action ?? "")) }
            } else {
                Button(node.label ?? "") { dispatch(CrossUiEvent.pressed(node.key, node.action ?? "")) }
                    .tint(node.variant == "secondary" ? .secondary : theme.primary ?? .accentColor)
            }
        }
        .disabled(!node.enabled)
        .accessibilityLabel(node.semantics.label ?? node.label ?? node.key)
        .accessibilityHint(node.semantics.hint ?? "")
    }

    // MARK: Toggle

    @ViewBuilder private var toggleView: some View {
        let binding = Binding(get: { node.checked }, set: { dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", $0 ? "true" : "false")) })
        Toggle(isOn: binding) { if let lbl = node.label { Text(lbl) } }
            .disabled(!node.enabled)
            .accessibilityLabel(node.semantics.label ?? node.label ?? node.key)
    }

    // MARK: Image

    @ViewBuilder private var imageView: some View {
        if let src = node.src, let url = URL(string: src) {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let img): img.resizable().scaledToFit()
                case .failure: Text("Unable to load image")
                case .empty: ProgressView()
                @unknown default: ProgressView()
                }
            }
            .accessibilityLabel(node.alt ?? node.semantics.label ?? "")
        }
    }

    // MARK: Slider

    @ViewBuilder private var sliderView: some View {
        let binding = Binding(
            get: { node.value.flatMap(Double.init) ?? (node.min ?? 0) },
            set: { dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", String($0))) }
        )
        let stepVal = node.step.flatMap { $0 > 0 ? $0 : nil }
        Slider(value: binding, in: (node.min ?? 0)...(node.max ?? 1), step: stepVal)
            .disabled(!node.enabled)
            .accessibilityLabel(node.semantics.label ?? node.key)
    }

    // MARK: Picker

    @ViewBuilder private var pickerView: some View {
        let binding = Binding(
            get: { node.selected ?? "" },
            set: { dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", $0)) }
        )
        Picker(node.semantics.label ?? "", selection: binding) {
            ForEach(node.options ?? [], id: \.value) { opt in
                Text(opt.label).tag(opt.value)
            }
        }
        .pickerStyle(.menu)
        .disabled(!node.enabled)
    }

    // MARK: DatePicker

    @ViewBuilder private var datePickerView: some View {
        let binding = Binding<Date>(
            get: {
                let raw = node.value ?? ""
                let fmt = ISO8601DateFormatter()
                return fmt.date(from: raw) ?? Date()
            },
            set: {
                let raw = ISO8601DateFormatter().string(from: $0)
                dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", raw))
            }
        )
        switch node.dateMode {
        case "time":
            DatePicker("", selection: binding, displayedComponents: .hourAndMinute)
                .labelsHidden()
        case "date":
            DatePicker("", selection: binding, displayedComponents: .date)
                .labelsHidden()
        default:
            DatePicker("", selection: binding)
                .labelsHidden()
        }
    }

    // MARK: Checkbox

    @ViewBuilder private var checkboxView: some View {
        let binding = Binding(get: { node.checked }, set: { dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", $0 ? "true" : "false")) })
        Toggle(isOn: binding) { if let lbl = node.label { Text(lbl) } }
            .toggleStyle(.checkbox)
            .disabled(!node.enabled)
            .accessibilityLabel(node.semantics.label ?? node.label ?? node.key)
    }

    // MARK: Card

    @ViewBuilder private var cardView: some View {
        VStack(alignment: .leading, spacing: 8) { children }
            .padding(16)
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    // MARK: Chip

    @ViewBuilder private var chipView: some View {
        let text = Text(node.label ?? node.key).font(.caption)
        if node.onDismiss != nil {
            HStack(spacing: 4) {
                text.padding(.horizontal, 12).padding(.vertical, 6)
                Button { dispatch(CrossUiEvent.pressed(node.key, node.onDismiss ?? "")) } label: {
                    Image(systemName: "xmark").font(.caption2)
                }
            }
            .background(Capsule().fill(node.variant == "filter" ? .blue.opacity(0.15) : .gray.opacity(0.15)))
        } else {
            text.padding(.horizontal, 12).padding(.vertical, 6)
                .background(Capsule().fill(.gray.opacity(0.15)))
        }
    }

    @ViewBuilder private func listItem(_ child: CrossUiNode) -> some View {
        let base: AnyView = if let action = node.onSelect {
            AnyView(Button { dispatch(CrossUiEvent.value(node.key, action, child.key)) } label: {
                CrossUiRenderer(node: child, theme: theme, platformView: platformView, dispatch: dispatch, presentDialog: presentDialog)
            })
        } else {
            AnyView(CrossUiRenderer(node: child, theme: theme, platformView: platformView, dispatch: dispatch, presentDialog: presentDialog))
        }
        // Platform extension: swipe_action
        if let swipeExt = child.iosExtension("swipe_action"),
           let swipeAction = swipeExt.fields["action"] as? String {
            base.swipeActions {
                Button(role: .destructive) {
                    dispatch(CrossUiEvent.pressed(child.key, swipeAction))
                } label: {
                    Label("Delete", systemImage: "trash")
                }
            }
        } else {
            base
        }
    }
}

// MARK: - View extension for conditional modifiers

extension View {
    func apply(_ transform: (Self) -> Self) -> Self { transform(self) }
}

// MARK: - Event helpers

private enum CrossUiEvent {
    static func pressed(_ key: String, _ action: String) -> String { encode(key, action, nil) }
    static func value(_ key: String, _ action: String, _ value: String) -> String { encode(key, action, value) }
    private static func encode(_ key: String, _ action: String, _ value: String?) -> String {
        var payload: [String: Any] = ["node_key": key, "action": ["type": action]]
        if let value { payload["action"] = ["type": action, "value": value] }
        let data = try! JSONSerialization.data(withJSONObject: payload)
        return String(decoding: data, as: UTF8.self)
    }
}
