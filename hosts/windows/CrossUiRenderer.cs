using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace CrossUi.Windows;

internal static class CrossUiRenderer
{
    private const int SupportedIrVersion = 1;
    private static readonly Dictionary<string, Func<JsonElement, UIElement>> PlatformViews = new();

    public static void RegisterPlatformView(string name, Func<JsonElement, UIElement> renderer)
    {
        PlatformViews[name] = renderer;
    }

    public static UIElement RenderDocument(string json, Action<string> dispatch)
    {
        using var document = JsonDocument.Parse(json);
        var payload = DocumentPayload(document.RootElement);
        ApplyTheme(payload);
        return RenderNode(payload.GetProperty("root"), dispatch);
    }

    /// Applies leaf-only keyed updates. Structural changes always return false so
    /// the host can safely rebuild the native tree.
    public static bool TryApplyPatch(Panel root, string json)
    {
        using var document = JsonDocument.Parse(json);
        var response = document.RootElement;
        if (!response.TryGetProperty("document", out _) || !response.TryGetProperty("patch", out var patch)) return false;
        var payload = DocumentPayload(response);

        var operations = patch.EnumerateArray().ToArray();
        if (operations.Length == 0) return true;
        if (operations.Any(operation => operation.GetProperty("op").GetString() != "update")) return false;

        foreach (var operation in operations)
        {
            var key = operation.GetProperty("key").GetString();
            if (string.IsNullOrWhiteSpace(key)) return false;
            var node = FindNode(payload.GetProperty("root"), key);
            var element = FindByKey(root, key);
            if (node is null || element is null || !UpdateLeaf(element, node.Value)) return false;
        }
        return true;
    }

    public static string[] NotificationMessages(string json)
    {
        using var document = JsonDocument.Parse(json);
        if (!document.RootElement.TryGetProperty("effects", out var effects)) return [];
        return effects.EnumerateArray()
            .Where(effect => effect.TryGetProperty("type", out var type) && type.GetString() == "notification")
            .Select(effect => effect.TryGetProperty("message", out var message) ? message.GetString() : null)
            .Where(message => !string.IsNullOrWhiteSpace(message))
            .Cast<string>()
            .ToArray();
    }

    private static UIElement RenderNode(JsonElement node, Action<string> dispatch)
    {
        var type = node.GetProperty("type").GetString();
        var element = type switch
        {
            "navigation" => RenderNavigation(node, dispatch),
            "route" => RenderRoute(node, dispatch),
            "stack" or "form" => RenderStack(node, dispatch),
            "list" => RenderList(node, dispatch),
            "loading" => RenderLoading(node),
            "text" => RenderText(node),
            "input" => RenderInput(node, dispatch),
            "button" => RenderButton(node, dispatch),
            "platform_view" => RenderPlatformView(node),
            _ => new TextBlock { Text = $"Unsupported CrossUI node: {type}" },
        };
        if (element is FrameworkElement frameworkElement)
            frameworkElement.Tag = node.GetProperty("key").GetString();
        return element;
    }

    private static UIElement RenderNavigation(JsonElement node, Action<string> dispatch)
    {
        var active = node.GetProperty("active").GetString();
        var route = Children(node).First(child => child.GetProperty("key").GetString() == active);
        return RenderNode(route, dispatch);
    }

    private static UIElement RenderPlatformView(JsonElement node)
    {
        var platform = node.GetProperty("platform").GetString();
        var name = node.GetProperty("name").GetString() ?? "unnamed";
        if (platform == "windows" && PlatformViews.TryGetValue(name, out var renderer))
        {
            var payload = node.TryGetProperty("payload", out var value) ? value.Clone() : default;
            return renderer(payload);
        }
        return new TextBlock { Text = $"Unsupported platform view: {name}" };
    }

    private static UIElement RenderRoute(JsonElement node, Action<string> dispatch)
    {
        var grid = new Grid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        var title = new TextBlock { Text = node.GetProperty("title").GetString(), Style = Application.Current.Resources["TitleTextBlockStyle"] as Style, Margin = new Thickness(24, 16, 24, 16) };
        grid.Children.Add(title);
        var content = new ScrollViewer { Content = RenderChildren(node, dispatch) };
        Grid.SetRow(content, 1);
        grid.Children.Add(content);
        return grid;
    }

    private static UIElement RenderStack(JsonElement node, Action<string> dispatch)
    {
        var isForm = node.GetProperty("type").GetString() == "form";
        var axis = isForm ? "vertical" : node.GetProperty("axis").GetString();
        var panel = RenderChildren(node, dispatch, axis == "horizontal" ? Orientation.Horizontal : Orientation.Vertical);
        panel.Spacing = isForm ? 12 : Spacing(node);
        panel.Margin = new Thickness(24);
        if (!isForm && node.TryGetProperty("alignment", out var alignment))
        {
            panel.HorizontalAlignment = alignment.GetString() switch
            {
                "start" => HorizontalAlignment.Left,
                "end" => HorizontalAlignment.Right,
                "stretch" => HorizontalAlignment.Stretch,
                _ => HorizontalAlignment.Center,
            };
        }
        return panel;
    }

    private static UIElement RenderList(JsonElement node, Action<string> dispatch)
    {
        var panel = new StackPanel { Spacing = 8 };
        var action = node.TryGetProperty("on_select", out var select) && select.ValueKind != JsonValueKind.Null ? select.GetString() : null;
        foreach (var child in Children(node))
        {
            var element = RenderNode(child, dispatch);
            if (action is null) panel.Children.Add(element);
            else
            {
                var key = child.GetProperty("key").GetString()!;
                var button = new Button { Content = element, HorizontalAlignment = HorizontalAlignment.Stretch };
                button.Click += (_, _) => dispatch(EventJson(node.GetProperty("key").GetString()!, action, key));
                panel.Children.Add(button);
            }
        }
        return panel;
    }

    private static UIElement RenderLoading(JsonElement node)
    {
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        panel.Children.Add(new ProgressRing { IsActive = true, Width = 24, Height = 24 });
        if (node.TryGetProperty("label", out var label) && label.ValueKind == JsonValueKind.String) panel.Children.Add(new TextBlock { Text = label.GetString() });
        return panel;
    }

    private static UIElement RenderText(JsonElement node)
    {
        var text = new TextBlock { Text = node.GetProperty("text").GetString(), TextWrapping = TextWrapping.Wrap };
        if (node.TryGetProperty("style", out var style) && style.GetString() == "title") text.Style = Application.Current.Resources["TitleTextBlockStyle"] as Style;
        return text;
    }

    private static UIElement RenderInput(JsonElement node, Action<string> dispatch)
    {
        var semantics = node.GetProperty("semantics");
        var label = semantics.TryGetProperty("label", out var semanticLabel) ? semanticLabel.GetString() : null;
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        var value = node.GetProperty("value").GetString() ?? "";
        var placeholder = node.TryGetProperty("placeholder", out var hint) && hint.ValueKind == JsonValueKind.String ? hint.GetString() : null;
        var enabled = semantics.GetProperty("enabled").GetBoolean();
        if (node.TryGetProperty("secure", out var secure) && secure.GetBoolean())
        {
            var input = new PasswordBox { Password = value, Header = label, PlaceholderText = placeholder, IsEnabled = enabled };
            AutomationProperties.SetName(input, label ?? placeholder ?? key);
            input.PasswordChanged += (_, _) => dispatch(EventJson(key, action, input.Password));
            return input;
        }
        var textInput = new TextBox { Text = value, Header = label, PlaceholderText = placeholder, IsEnabled = enabled };
        AutomationProperties.SetName(textInput, label ?? placeholder ?? key);
        textInput.TextChanged += (_, _) => dispatch(EventJson(key, action, textInput.Text));
        return textInput;
    }

    private static UIElement RenderButton(JsonElement node, Action<string> dispatch)
    {
        var button = new Button { Content = node.GetProperty("label").GetString(), HorizontalAlignment = HorizontalAlignment.Stretch, IsEnabled = node.GetProperty("semantics").GetProperty("enabled").GetBoolean() };
        var variant = node.TryGetProperty("variant", out var value) ? value.GetString() : "primary";
        if (variant == "destructive")
            button.Background = new SolidColorBrush(Microsoft.UI.Colors.IndianRed);
        else if (variant != "secondary" && Application.Current.Resources.TryGetValue("CrossUiPrimaryBrush", out var primary) && primary is Brush brush)
            button.Background = brush;
        AutomationProperties.SetName(button, node.GetProperty("semantics").TryGetProperty("label", out var label) ? label.GetString() : node.GetProperty("label").GetString());
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("action").GetString()!;
        button.Click += (_, _) => dispatch(EventJson(key, action));
        return button;
    }

    private static StackPanel RenderChildren(JsonElement node, Action<string> dispatch, Orientation orientation = Orientation.Vertical)
    {
        var panel = new StackPanel { Spacing = 16, Orientation = orientation };
        foreach (var child in Children(node)) panel.Children.Add(RenderNode(child, dispatch));
        return panel;
    }

    private static JsonElement[] Children(JsonElement node) => node.TryGetProperty("children", out var children) ? children.EnumerateArray().ToArray() : [];

    private static JsonElement DocumentPayload(JsonElement response)
    {
        var payload = response.TryGetProperty("document", out var update) ? update : response;
        if (!payload.TryGetProperty("version", out var version) || version.GetInt32() != SupportedIrVersion)
            throw new InvalidOperationException($"Unsupported CrossUI IR version: {payload.GetRawText()}");
        return payload;
    }

    private static void ApplyTheme(JsonElement document)
    {
        var resources = Application.Current.Resources;
        if (!document.TryGetProperty("theme", out var theme)
            || !theme.TryGetProperty("tokens", out var tokens)
            || !tokens.TryGetProperty("primary", out var primary)
            || primary.ValueKind != JsonValueKind.String
            || !TryParseColor(primary.GetString(), out var color))
        {
            resources.Remove("CrossUiPrimaryBrush");
            return;
        }
        resources["CrossUiPrimaryBrush"] = new SolidColorBrush(color);
    }

    private static bool TryParseColor(string? value, out global::Windows.UI.Color color)
    {
        color = default;
        if (string.IsNullOrWhiteSpace(value) || value.Length != 7 || value[0] != '#') return false;
        if (!byte.TryParse(value.AsSpan(1, 2), System.Globalization.NumberStyles.HexNumber, null, out var red)
            || !byte.TryParse(value.AsSpan(3, 2), System.Globalization.NumberStyles.HexNumber, null, out var green)
            || !byte.TryParse(value.AsSpan(5, 2), System.Globalization.NumberStyles.HexNumber, null, out var blue)) return false;
        color = global::Windows.UI.Color.FromArgb(255, red, green, blue);
        return true;
    }

    private static JsonElement? FindNode(JsonElement node, string key)
    {
        if (node.GetProperty("key").GetString() == key) return node;
        foreach (var child in Children(node))
        {
            var match = FindNode(child, key);
            if (match is not null) return match;
        }
        return null;
    }

    private static UIElement? FindByKey(DependencyObject root, string key)
    {
        if (root is FrameworkElement element && element.Tag as string == key) return element;
        for (var index = 0; index < VisualTreeHelper.GetChildrenCount(root); index++)
        {
            var match = FindByKey(VisualTreeHelper.GetChild(root, index), key);
            if (match is not null) return match;
        }
        return null;
    }

    private static bool UpdateLeaf(UIElement element, JsonElement node)
    {
        var type = node.GetProperty("type").GetString();
        var enabled = node.TryGetProperty("semantics", out var semantics) && semantics.TryGetProperty("enabled", out var semanticEnabled) && semanticEnabled.GetBoolean();
        switch (type, element)
        {
            case ("text", TextBlock text):
                text.Text = node.GetProperty("text").GetString();
                return true;
            case ("button", Button button):
                button.Content = node.GetProperty("label").GetString();
                button.IsEnabled = enabled;
                return true;
            case ("input", TextBox input):
                var value = node.GetProperty("value").GetString() ?? "";
                if (input.Text != value) input.Text = value;
                input.IsEnabled = enabled;
                return true;
            case ("input", PasswordBox input):
                var password = node.GetProperty("value").GetString() ?? "";
                if (input.Password != password) input.Password = password;
                input.IsEnabled = enabled;
                return true;
            default:
                return false;
        }
    }

    private static double Spacing(JsonElement node) => node.TryGetProperty("spacing", out var spacing) && spacing.ValueKind == JsonValueKind.String
        ? spacing.GetString() switch { "spacing.sm" => 8, "spacing.lg" => 24, _ => 16 }
        : 16;

    private static string EventJson(string nodeKey, string action, string? value = null)
    {
        var payload = new JsonObject { ["node_key"] = nodeKey, ["action"] = new JsonObject { ["type"] = action } };
        if (value is not null) payload["action"]!["value"] = value;
        return payload.ToJsonString();
    }
}
