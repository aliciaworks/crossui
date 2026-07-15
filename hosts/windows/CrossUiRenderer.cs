using System;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace CrossUi.Windows;

internal static class CrossUiRenderer
{
    public static UIElement RenderDocument(string json, Action<string> dispatch)
    {
        using var document = JsonDocument.Parse(json);
        return RenderNode(document.RootElement.GetProperty("root"), dispatch);
    }

    private static UIElement RenderNode(JsonElement node, Action<string> dispatch)
    {
        var type = node.GetProperty("type").GetString();
        return type switch
        {
            "navigation" => RenderNavigation(node, dispatch),
            "route" => RenderRoute(node, dispatch),
            "stack" or "form" => RenderStack(node, dispatch),
            "list" => RenderList(node, dispatch),
            "loading" => RenderLoading(node),
            "text" => RenderText(node),
            "input" => RenderInput(node, dispatch),
            "button" => RenderButton(node, dispatch),
            _ => new TextBlock { Text = $"Unsupported CrossUI node: {type}" },
        };
    }

    private static UIElement RenderNavigation(JsonElement node, Action<string> dispatch)
    {
        var active = node.GetProperty("active").GetString();
        var route = Children(node).First(child => child.GetProperty("key").GetString() == active);
        return RenderNode(route, dispatch);
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
        var panel = RenderChildren(node, dispatch);
        panel.Spacing = node.GetProperty("type").GetString() == "form" ? 12 : 16;
        panel.Margin = new Thickness(24);
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
        var input = new TextBox { Text = node.GetProperty("value").GetString(), Header = semantics.TryGetProperty("label", out var label) ? label.GetString() : null, IsEnabled = semantics.GetProperty("enabled").GetBoolean() };
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        input.TextChanged += (_, _) => dispatch(EventJson(key, action, input.Text));
        return input;
    }

    private static UIElement RenderButton(JsonElement node, Action<string> dispatch)
    {
        var button = new Button { Content = node.GetProperty("label").GetString(), HorizontalAlignment = HorizontalAlignment.Stretch, IsEnabled = node.GetProperty("semantics").GetProperty("enabled").GetBoolean() };
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("action").GetString()!;
        button.Click += (_, _) => dispatch(EventJson(key, action));
        return button;
    }

    private static StackPanel RenderChildren(JsonElement node, Action<string> dispatch)
    {
        var panel = new StackPanel { Spacing = 16 };
        foreach (var child in Children(node)) panel.Children.Add(RenderNode(child, dispatch));
        return panel;
    }

    private static JsonElement[] Children(JsonElement node) => node.TryGetProperty("children", out var children) ? children.EnumerateArray().ToArray() : [];

    private static string EventJson(string nodeKey, string action, string? value = null)
    {
        var payload = new JsonObject { ["node_key"] = nodeKey, ["action"] = new JsonObject { ["type"] = action } };
        if (value is not null) payload["action"]!["value"] = value;
        return payload.ToJsonString();
    }
}
