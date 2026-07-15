import SwiftUI

protocol CrossUiDocumentProvider {
    func initialDocument() throws -> String
    func dispatchEvent(_ event: String) throws -> String
}

struct CrossUiDocument: Decodable {
    let version: Int
    let root: CrossUiNode
    let theme: CrossUiTheme

    static let supportedVersion = 1

    static func decode(_ json: String) throws -> CrossUiDocument {
        try CrossUiUpdate.decode(json).document
    }
}

struct CrossUiTheme: Decodable {
    let tokens: [String: CrossUiToken]

    init(tokens: [String: CrossUiToken] = [:]) { self.tokens = tokens }

    var primary: Color? {
        guard case let .color(value)? = tokens["primary"] else { return nil }
        return Color(crossUiHex: value)
    }
}

enum CrossUiToken: Decodable {
    case color(String)
    case number(Int)

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let value = try? container.decode(String.self) { self = .color(value) }
        else { self = .number(try container.decode(Int.self)) }
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
            let effects = (response?["effects"] as? [[String: Any]] ?? []).map { CrossUiEffect(type: $0["type"] as? String ?? "", message: $0["message"] as? String) }
            return CrossUiUpdate(document: document, effects: effects)
        }
        let document = try JSONDecoder().decode(CrossUiDocument.self, from: data)
        guard document.version == CrossUiDocument.supportedVersion else { throw CrossUiDocumentError.unsupportedVersion(document.version) }
        return CrossUiUpdate(document: document, effects: [])
    }
}

private enum CrossUiDocumentError: Error { case unsupportedVersion(Int) }

struct CrossUiNode: Decodable, Identifiable {
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
    let semantics: CrossUiSemantics
    let enabled: Bool
    let children: [CrossUiNode]

    var id: String { key }

    enum CodingKeys: String, CodingKey {
        case key, type, text, style, title, label, value, action, variant, active, children, semantics, placeholder, secure, axis, alignment, spacing, platform, name, payload
        case onChange = "on_change"
        case onSelect = "on_select"
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        key = try values.decode(String.self, forKey: .key)
        type = try values.decode(String.self, forKey: .type)
        text = try values.decodeIfPresent(String.self, forKey: .text)
        style = try values.decodeIfPresent(String.self, forKey: .style)
        title = try values.decodeIfPresent(String.self, forKey: .title)
        label = try values.decodeIfPresent(String.self, forKey: .label)
        value = try values.decodeIfPresent(String.self, forKey: .value)
        action = try values.decodeIfPresent(String.self, forKey: .action)
        variant = try values.decodeIfPresent(String.self, forKey: .variant)
        onChange = try values.decodeIfPresent(String.self, forKey: .onChange)
        onSelect = try values.decodeIfPresent(String.self, forKey: .onSelect)
        active = try values.decodeIfPresent(String.self, forKey: .active)
        axis = try values.decodeIfPresent(String.self, forKey: .axis)
        alignment = try values.decodeIfPresent(String.self, forKey: .alignment)
        spacing = try values.decodeIfPresent(String.self, forKey: .spacing)
        platform = try values.decodeIfPresent(String.self, forKey: .platform)
        name = try values.decodeIfPresent(String.self, forKey: .name)
        payload = try values.decodeIfPresent(CrossUiJsonValue.self, forKey: .payload)
        placeholder = try values.decodeIfPresent(String.self, forKey: .placeholder)
        secure = try values.decodeIfPresent(Bool.self, forKey: .secure) ?? false
        children = try values.decodeIfPresent([CrossUiNode].self, forKey: .children) ?? []
        semantics = try values.decodeIfPresent(CrossUiSemantics.self, forKey: .semantics) ?? CrossUiSemantics()
        enabled = semantics.enabled ?? true
    }
}

private struct CrossUiSemantics: Decodable {
    let label: String?
    let hint: String?
    let role: String?
    let enabled: Bool?

    init(label: String? = nil, hint: String? = nil, role: String? = nil, enabled: Bool? = nil) {
        self.label = label
        self.hint = hint
        self.role = role
        self.enabled = enabled
    }
}

struct CrossUiHost: View {
    let provider: any CrossUiDocumentProvider
    let platformView: (CrossUiNode) -> AnyView?
    @State private var document: CrossUiDocument
    @State private var notification: String?

    init(provider: any CrossUiDocumentProvider, platformView: @escaping (CrossUiNode) -> AnyView? = { _ in nil }) {
        self.provider = provider
        self.platformView = platformView
        _document = State(initialValue: (try? provider.initialDocument()).flatMap { try? CrossUiDocument.decode($0) } ?? CrossUiDocument(version: CrossUiDocument.supportedVersion, root: CrossUiNode.empty, theme: CrossUiTheme()))
    }

    var body: some View {
        CrossUiRenderer(node: document.root, theme: document.theme, platformView: platformView, dispatch: dispatch)
            .alert("CrossUI", isPresented: Binding(get: { notification != nil }, set: { if !$0 { notification = nil } })) {
                Button("OK", role: .cancel) { notification = nil }
            } message: {
                Text(notification ?? "")
            }
    }

    private func dispatch(_ event: String) {
        guard let json = try? provider.dispatchEvent(event), let update = try? CrossUiUpdate.decode(json) else { return }
        document = update.document
        notification = update.effects.first(where: { $0.type == "notification" })?.message
    }
}

private extension CrossUiNode {
    static let empty = CrossUiNode(key: "empty", type: "text", text: "Unable to load CrossUI document", style: nil, title: nil, label: nil, value: nil, action: nil, variant: nil, onChange: nil, onSelect: nil, active: nil, axis: nil, alignment: nil, spacing: nil, platform: nil, name: nil, payload: nil, placeholder: nil, secure: false, semantics: CrossUiSemantics(), enabled: true, children: [])
}

private extension CrossUiNode {
    init(key: String, type: String, text: String?, style: String?, title: String?, label: String?, value: String?, action: String?, variant: String?, onChange: String?, onSelect: String?, active: String?, axis: String?, alignment: String?, spacing: String?, platform: String?, name: String?, payload: CrossUiJsonValue?, placeholder: String?, secure: Bool, semantics: CrossUiSemantics, enabled: Bool, children: [CrossUiNode]) {
        self.key = key; self.type = type; self.text = text; self.style = style; self.title = title; self.label = label; self.value = value; self.action = action; self.variant = variant; self.onChange = onChange; self.onSelect = onSelect; self.active = active; self.axis = axis; self.alignment = alignment; self.spacing = spacing; self.platform = platform; self.name = name; self.payload = payload; self.placeholder = placeholder; self.secure = secure; self.semantics = semantics; self.enabled = enabled; self.children = children
    }
}

struct CrossUiRenderer: View {
    let node: CrossUiNode
    let theme: CrossUiTheme
    let platformView: (CrossUiNode) -> AnyView?
    let dispatch: (String) -> Void

    var body: some View {
        switch node.type {
        case "navigation": if let route = node.children.first(where: { $0.key == node.active }) { CrossUiRenderer(node: route, theme: theme, platformView: platformView, dispatch: dispatch) }
        case "route": NavigationStack { ScrollView { children }.navigationTitle(node.title ?? "") }
        case "stack": stack
        case "form": VStack(alignment: .leading, spacing: 12) { children }
        case "list": VStack(alignment: .leading, spacing: 8) { ForEach(node.children) { child in listItem(child) } }
        case "loading": HStack { ProgressView(); if let label = node.label { Text(label) } }
        case "text": Text(node.text ?? "").font(node.style == "title" ? .title : .body).accessibilityAddTraits(node.semantics.role == "header" ? .isHeader : [])
        case "input": input
        case "button": button
        case "platform_view": if node.platform == "ios", let view = platformView(node) { view } else { Text("Unsupported platform view: \(node.key)") }
        default: Text("Unsupported CrossUI node: \(node.type)")
        }
    }

    @ViewBuilder private var children: some View { ForEach(node.children) { CrossUiRenderer(node: $0, theme: theme, platformView: platformView, dispatch: dispatch) } }

    @ViewBuilder private var stack: some View {
        if node.axis == "horizontal" { HStack(alignment: horizontalAlignment, spacing: resolvedSpacing) { children } }
        else { VStack(alignment: verticalAlignment, spacing: resolvedSpacing) { children } }
    }

    private var resolvedSpacing: CGFloat {
        switch node.spacing {
        case "spacing.sm": 8
        case "spacing.lg": 24
        default: 16
        }
    }

    private var horizontalAlignment: VerticalAlignment {
        switch node.alignment {
        case "start": .top
        case "end": .bottom
        default: .center
        }
    }

    private var verticalAlignment: HorizontalAlignment {
        switch node.alignment {
        case "center": .center
        case "end": .trailing
        default: .leading
        }
    }

    @ViewBuilder private var input: some View {
        let binding = Binding(get: { node.value ?? "" }, set: { dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", $0)) })
        Group {
            if node.secure { SecureField(node.placeholder ?? node.label ?? "", text: binding) }
            else { TextField(node.placeholder ?? node.label ?? "", text: binding) }
        }
        .disabled(!node.enabled)
        .accessibilityLabel(node.semantics.label ?? node.label ?? node.key)
        .accessibilityHint(node.semantics.hint ?? "")
    }

    @ViewBuilder private var button: some View {
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

    @ViewBuilder private func listItem(_ child: CrossUiNode) -> some View {
        if let action = node.onSelect { Button { dispatch(CrossUiEvent.value(node.key, action, child.key)) } label: { CrossUiRenderer(node: child, theme: theme, platformView: platformView, dispatch: dispatch) } }
        else { CrossUiRenderer(node: child, theme: theme, platformView: platformView, dispatch: dispatch) }
    }
}

private enum CrossUiEvent {
    static func pressed(_ nodeKey: String, _ action: String) -> String { encode(nodeKey, action, nil) }
    static func value(_ nodeKey: String, _ action: String, _ value: String) -> String { encode(nodeKey, action, value) }
    private static func encode(_ nodeKey: String, _ action: String, _ value: String?) -> String {
        var payload: [String: Any] = ["node_key": nodeKey, "action": ["type": action]]
        if let value { payload["action"] = ["type": action, "value": value] }
        let data = try! JSONSerialization.data(withJSONObject: payload)
        return String(decoding: data, as: UTF8.self)
    }
}
