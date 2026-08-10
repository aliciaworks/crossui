package dev.crossui.compiler

import dev.crossui.ir.LocalizedField
import dev.crossui.ir.ButtonVariant
import dev.crossui.ir.MotionPreset
import dev.crossui.ir.NavigationMode
import dev.crossui.ir.Node
import dev.crossui.ir.NodeKind
import dev.crossui.ir.TextStyle
import dev.crossui.ir.walk

internal fun MotionPreset.winUiTransition(): String = when (this) {
    MotionPreset.Default, MotionPreset.Fade -> "ContentThemeTransition"
    MotionPreset.Scale, MotionPreset.Blend -> "PopupThemeTransition"
    MotionPreset.SlideUp -> "EntranceThemeTransition"
}

internal data class WinUiNavigation(
    val name: String,
    val active: String,
    val routes: List<Node>,
    val bindingProperty: String?,
    val onChange: String,
)

internal fun winUiRootLayout(
    typeName: String,
    body: String,
    rootIsNavigation: Boolean,
): String {
    if (rootIsNavigation) return body
    val contentName = "${typeName.identifier()}ContentRoot"
    return """
        |    <ScrollViewer
        |        HorizontalScrollBarVisibility="Disabled"
        |        HorizontalContentAlignment="Stretch"
        |        VerticalScrollBarVisibility="Auto">
        |        <Grid x:Name="$contentName" Padding="32,24,32,32">
        |${winUiAdaptiveMargins(contentName, 3)}
        |            <Grid MaxWidth="840" HorizontalAlignment="Center">
        |$body
        |            </Grid>
        |        </Grid>
        |    </ScrollViewer>
    """.trimMargin()
}

internal fun winUiNavigation(
    node: Node,
    kind: NodeKind.Navigation,
    depth: Int,
    indentation: String,
    render: (Node, Int) -> String,
): String {
    val selected = node.children.firstOrNull { it.key.value == kind.active }
        ?: node.children.firstOrNull()
    if (kind.mode != NavigationMode.Tab) {
        return selected?.let { render(it, depth) }.orEmpty()
    }
    val name = "CrossUiNavigation${node.key.value.identifier()}"
    val contentName = "${name}ContentRoot"
    val menuItems = node.children.joinToString("\n") { route ->
        val title = (route.kind as? NodeKind.Route)?.title ?: route.key.value
        val initial = title.firstOrNull()?.toString().orEmpty().xml()
        """
        |$indentation        <NavigationViewItem
        |$indentation            Content="${route.xamlText(LocalizedField.Title, title)}"
        |$indentation            Tag="${route.key.value.xml()}">
        |$indentation            <NavigationViewItem.Icon>
        |$indentation                <FontIcon FontFamily="Segoe UI" Glyph="$initial" />
        |$indentation            </NavigationViewItem.Icon>
        |$indentation        </NavigationViewItem>
        """.trimMargin()
    }
    val routes = node.children.joinToString("\n") { route ->
        val routeName = "${name}Route${route.key.value.identifier()}"
        val visibility = if (route === selected) "Visible" else "Collapsed"
        """
        |$indentation                <Grid x:Name="$routeName" Visibility="$visibility">
        |${render(route, depth + 5)}
        |$indentation                </Grid>
        """.trimMargin()
    }
    return """
        |${indentation}<NavigationView
        |${indentation}    x:Name="$name"
        |${indentation}    IsBackButtonVisible="Collapsed"
        |${indentation}    IsSettingsVisible="False"
        |${indentation}    PaneDisplayMode="Auto"
        |${indentation}    SelectionChanged="On${name}SelectionChanged">
        |${indentation}    <NavigationView.MenuItems>
        |$menuItems
        |${indentation}    </NavigationView.MenuItems>
        |${indentation}    <NavigationView.Content>
        |${indentation}        <ScrollViewer
        |${indentation}            HorizontalScrollBarVisibility="Disabled"
        |${indentation}            HorizontalContentAlignment="Stretch"
        |${indentation}            VerticalScrollBarVisibility="Auto">
        |${indentation}            <Grid x:Name="$contentName" Padding="32,24,32,32">
        |${winUiAdaptiveMargins(contentName, depth + 4)}
        |${indentation}                <Grid MaxWidth="1064" HorizontalAlignment="Center">
        |$routes
        |${indentation}                </Grid>
        |${indentation}            </Grid>
        |${indentation}        </ScrollViewer>
        |${indentation}    </NavigationView.Content>
        |${indentation}</NavigationView>
    """.trimMargin()
}

private fun winUiAdaptiveMargins(name: String, depth: Int): String {
    val i = "    ".repeat(depth)
    return """
        |${i}<VisualStateManager.VisualStateGroups>
        |${i}    <VisualStateGroup x:Name="${name}LayoutStates">
        |${i}        <VisualState x:Name="${name}Narrow">
        |${i}            <VisualState.StateTriggers>
        |${i}                <AdaptiveTrigger MinWindowWidth="0" />
        |${i}            </VisualState.StateTriggers>
        |${i}            <VisualState.Setters>
        |${i}                <Setter Target="$name.Padding" Value="16,16,16,24" />
        |${i}            </VisualState.Setters>
        |${i}        </VisualState>
        |${i}        <VisualState x:Name="${name}Expanded">
        |${i}            <VisualState.StateTriggers>
        |${i}                <AdaptiveTrigger MinWindowWidth="640" />
        |${i}            </VisualState.StateTriggers>
        |${i}        </VisualState>
        |${i}    </VisualStateGroup>
        |${i}</VisualStateManager.VisualStateGroups>
    """.trimMargin()
}

internal fun Node.winUiNavigations(): List<WinUiNavigation> = buildList {
    walk { node ->
        val kind = node.kind as? NodeKind.Navigation ?: return@walk
        if (kind.mode == NavigationMode.Tab) {
            add(
                WinUiNavigation(
                    name = "CrossUiNavigation${node.key.value.identifier()}",
                    active = kind.active,
                    routes = node.children,
                    bindingProperty = node.bindings["active"]?.path?.identifier(),
                    onChange = kind.onChange,
                ),
            )
        }
    }
}

internal fun TextStyle.winUiTextStyle(): String = when (this) {
    TextStyle.Display -> "DisplayTextBlockStyle"
    TextStyle.Headline -> "HeaderTextBlockStyle"
    TextStyle.Title -> "TitleTextBlockStyle"
    TextStyle.Body -> "BodyTextBlockStyle"
    TextStyle.Caption -> "CaptionTextBlockStyle"
    TextStyle.Footnote -> "CaptionTextBlockStyle"
}

internal fun ButtonVariant.winUiButtonStyle(): String = when (this) {
    ButtonVariant.Primary -> " Style=\"{StaticResource AccentButtonStyle}\""
    ButtonVariant.Secondary, ButtonVariant.Destructive -> ""
}

internal fun winUiNavigationMembers(navigation: WinUiNavigation): String {
    val routeVisibility = navigation.routes.joinToString("\n") { route ->
        val routeName = "${navigation.name}Route${route.key.value.identifier()}"
        "        $routeName.Visibility = route == \"${route.key.value.csharp()}\" " +
            "? Visibility.Visible : Visibility.Collapsed;"
    }
    val selectionAction = navigation.bindingProperty?.let {
        "State.$it = route;"
    } ?: "Dispatch(\"${navigation.onChange.csharp()}\", route);"
    return """
        |    private void On${navigation.name}SelectionChanged(
        |        NavigationView sender,
        |        NavigationViewSelectionChangedEventArgs args
        |    )
        |    {
        |        if (args.SelectedItemContainer?.Tag is not string route)
        |        {
        |            return;
        |        }
        |        Show${navigation.name}Route(route);
        |        if (!suppressNavigationSelection)
        |        {
        |            $selectionAction
        |        }
        |    }
        |
        |    public void Apply${navigation.name}Selection(string route)
        |    {
        |        var wasSuppressed = suppressNavigationSelection;
        |        suppressNavigationSelection = true;
        |        foreach (var rawItem in ${navigation.name}.MenuItems)
        |        {
        |            if (rawItem is NavigationViewItem item &&
        |                item.Tag?.ToString() == route)
        |            {
        |                ${navigation.name}.SelectedItem = item;
        |                break;
        |            }
        |        }
        |        Show${navigation.name}Route(route);
        |        suppressNavigationSelection = wasSuppressed;
        |    }
        |
        |    private void Show${navigation.name}Route(string route)
        |    {
        |$routeVisibility
        |    }
    """.trimMargin()
}
