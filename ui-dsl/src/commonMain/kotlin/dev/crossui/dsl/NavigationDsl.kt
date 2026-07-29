package dev.crossui.dsl

import dev.crossui.ir.LocalizedField
import dev.crossui.ir.LocalizedText
import dev.crossui.ir.NavigationMode
import dev.crossui.ir.Node
import dev.crossui.ir.NodeKey
import dev.crossui.ir.NodeKind

fun tabNavigation(key: String, active: String, routes: List<Node>) =
    Node(NodeKey(key), NodeKind.Navigation(active, NavigationMode.Tab), children = routes)

fun tabNavigation(
    key: String,
    active: StateBinding<String>,
    onChange: String,
    routes: List<Node>,
) = Node(
    NodeKey(key),
    NodeKind.Navigation(
        active = routes.firstOrNull()?.key?.value.orEmpty(),
        mode = NavigationMode.Tab,
        onChange = onChange,
    ),
    children = routes,
).withBinding("active", active)

fun stackNavigation(key: String, active: String, routes: List<Node>) =
    Node(NodeKey(key), NodeKind.Navigation(active, NavigationMode.Stack), children = routes)

fun navigation(key: String, active: String, routes: List<Node>) =
    tabNavigation(key, active, routes)

fun route(key: String, title: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.Route(title), children = children)

fun route(key: String, title: String, block: ChildrenBuilder.() -> Unit) =
    route(key, title, ui(block))

fun route(key: String, title: LocalizedText, children: List<Node>) =
    Node(
        NodeKey(key),
        NodeKind.Route(title.fallback),
        children = children,
        localizedText = mapOf(LocalizedField.Title to title),
    )

fun route(key: String, title: LocalizedText, block: ChildrenBuilder.() -> Unit) =
    route(key, title, ui(block))

fun fullscreenRoute(key: String, title: String, children: List<Node>) =
    Node(NodeKey(key), NodeKind.Route(title, respectSafeArea = false), children = children)
