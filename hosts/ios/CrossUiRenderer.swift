import SwiftUI

protocol CrossUiDocumentProvider {
    func initialDocument() throws -> String
    func dispatchEvent(_ event: String) throws -> String
}

struct CrossUiDocument: Decodable {
    let root: CrossUiNode

    static func decode(_ json: String) throws -> CrossUiDocument {
        try JSONDecoder().decode(CrossUiDocument.self, from: Data(json.utf8))
    }
}

struct CrossUiNode: Decodable, Identifiable {
    let key: String
    let type: String
    let text: String?
    let style: String?
    let title: String?
    let label: String?
    let value: String?
    let action: String?
    let onChange: String?
    let onSelect: String?
    let active: String?
    let enabled: Bool
    let children: [CrossUiNode]

    var id: String { key }

    enum CodingKeys: String, CodingKey {
        case key, type, text, style, title, label, value, action, active, children, semantics
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
        onChange = try values.decodeIfPresent(String.self, forKey: .onChange)
        onSelect = try values.decodeIfPresent(String.self, forKey: .onSelect)
        active = try values.decodeIfPresent(String.self, forKey: .active)
        children = try values.decodeIfPresent([CrossUiNode].self, forKey: .children) ?? []
        let semantics = try values.decodeIfPresent(CrossUiSemantics.self, forKey: .semantics)
        enabled = semantics?.enabled ?? true
    }
}

private struct CrossUiSemantics: Decodable { let enabled: Bool? }

struct CrossUiHost: View {
    let provider: any CrossUiDocumentProvider
    @State private var document: CrossUiDocument

    init(provider: any CrossUiDocumentProvider) {
        self.provider = provider
        _document = State(initialValue: (try? provider.initialDocument()).flatMap { try? CrossUiDocument.decode($0) } ?? CrossUiDocument(root: CrossUiNode.empty))
    }

    var body: some View {
        CrossUiRenderer(node: document.root, dispatch: dispatch)
    }

    private func dispatch(_ event: String) {
        guard let json = try? provider.dispatchEvent(event), let next = try? CrossUiDocument.decode(json) else { return }
        document = next
    }
}

private extension CrossUiNode {
    static let empty = CrossUiNode(key: "empty", type: "text", text: "Unable to load CrossUI document", style: nil, title: nil, label: nil, value: nil, action: nil, onChange: nil, onSelect: nil, active: nil, enabled: true, children: [])
}

private extension CrossUiNode {
    init(key: String, type: String, text: String?, style: String?, title: String?, label: String?, value: String?, action: String?, onChange: String?, onSelect: String?, active: String?, enabled: Bool, children: [CrossUiNode]) {
        self.key = key; self.type = type; self.text = text; self.style = style; self.title = title; self.label = label; self.value = value; self.action = action; self.onChange = onChange; self.onSelect = onSelect; self.active = active; self.enabled = enabled; self.children = children
    }
}

struct CrossUiRenderer: View {
    let node: CrossUiNode
    let dispatch: (String) -> Void

    var body: some View {
        switch node.type {
        case "navigation": if let route = node.children.first(where: { $0.key == node.active }) { CrossUiRenderer(node: route, dispatch: dispatch) }
        case "route": NavigationStack { ScrollView { children }.navigationTitle(node.title ?? "") }
        case "stack", "form": VStack(alignment: .leading, spacing: node.type == "form" ? 12 : 16) { children }
        case "list": VStack(alignment: .leading, spacing: 8) { ForEach(node.children) { child in listItem(child) } }
        case "loading": HStack { ProgressView(); if let label = node.label { Text(label) } }
        case "text": Text(node.text ?? "").font(node.style == "title" ? .title : .body)
        case "input": TextField(node.label ?? "", text: Binding(get: { node.value ?? "" }, set: { dispatch(CrossUiEvent.value(node.key, node.onChange ?? "", $0)) })).disabled(!node.enabled)
        case "button": Button(node.label ?? "") { dispatch(CrossUiEvent.pressed(node.key, node.action ?? "")) }.disabled(!node.enabled)
        default: EmptyView()
        }
    }

    @ViewBuilder private var children: some View { ForEach(node.children) { CrossUiRenderer(node: $0, dispatch: dispatch) } }

    @ViewBuilder private func listItem(_ child: CrossUiNode) -> some View {
        if let action = node.onSelect { Button { dispatch(CrossUiEvent.value(node.key, action, child.key)) } label: { CrossUiRenderer(node: child, dispatch: dispatch) } }
        else { CrossUiRenderer(node: child, dispatch: dispatch) }
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
