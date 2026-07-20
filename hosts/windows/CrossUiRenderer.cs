using System;
using System.Collections.Generic;
using System.Linq;
using System.Text.Json;
using System.Text.Json.Nodes;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Automation;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Media.Imaging;

namespace CrossUi.Windows;

internal static class CrossUiRenderer
{
    private const int SupportedIrVersion = 2;
    private static readonly Dictionary<string, Func<JsonElement, UIElement>> PlatformViews = new();

    public static void RegisterPlatformView(string name, Func<JsonElement, UIElement> renderer)
    {
        PlatformViews[name] = renderer;
    }

    // ---- Document-level entry points -----------------------------------------

    public static UIElement RenderDocument(string json, Action<string> dispatch)
    {
        using var document = JsonDocument.Parse(json);
        var payload = DocumentPayload(document.RootElement);
        ApplyTheme(payload);
        return RenderNode(payload.GetProperty("root"), dispatch);
    }

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

    // ---- Node dispatcher -----------------------------------------------------

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
            "toggle" => RenderToggle(node, dispatch),
            "image" => RenderImage(node),
            "slider" => RenderSlider(node, dispatch),
            "picker" => RenderPicker(node, dispatch),
            "date_picker" => RenderDatePicker(node, dispatch),
            "dialog" => RenderDialog(node, dispatch),
            "checkbox" => RenderCheckbox(node, dispatch),
            "divider" => RenderDivider(node),
            "card" => RenderCard(node, dispatch),
            "chip" => RenderChip(node, dispatch),
            "platform_view" => RenderPlatformView(node),
            _ => new TextBlock { Text = $"Unsupported CrossUI node: {type}" },
        };
        if (element is FrameworkElement frameworkElement)
            frameworkElement.Tag = node.GetProperty("key").GetString();
        // Apply Windows platform extensions.
        ApplyWindowsExtensions(element, node);
        return element;
    }

    private static void ApplyWindowsExtensions(UIElement element, JsonElement node)
    {
        if (!node.TryGetProperty("extensions", out var exts) || exts.ValueKind != JsonValueKind.Array)
            return;
        foreach (var ext in exts.EnumerateArray())
        {
            var platform = ext.GetProperty("platform").GetString();
            if (platform != "windows") continue;
            var data = ext.GetProperty("data");
            var type = data.GetProperty("type").GetString();
            switch (type)
            {
                case "corner_preference":
                    if (element is Control control && data.TryGetProperty("radius", out var radiusEl))
                        control.CornerRadius = new CornerRadius(radiusEl.GetSingle());
                    else if (element is Border border && data.TryGetProperty("radius", out var borderRadius))
                        border.CornerRadius = new CornerRadius(borderRadius.GetSingle());
                    break;
                case "connected_animation":
                    if (element is FrameworkElement fe && data.TryGetProperty("key", out var animKey))
                        ConnectedAnimationService.GetForCurrentView().PrepareToAnimate(
                            animKey.GetString()!, fe);
                    break;
            }
        }
    }

    // ---- Navigation (Tab vs Stack) ------------------------------------------

    private static UIElement RenderNavigation(JsonElement node, Action<string> dispatch)
    {
        var active = node.GetProperty("active").GetString();
        var mode = node.TryGetProperty("mode", out var m) ? m.GetString() : "tab";

        if (mode == "stack")
        {
            var route = Children(node).First(child => child.GetProperty("key").GetString() == active);
            return RenderNode(route, dispatch);
        }

        // Tab mode: use Pivot for tab-bar navigation.
        var pivot = new Pivot();
        foreach (var route in Children(node))
        {
            var routeKey = route.GetProperty("key").GetString() ?? "untitled";
            var page = RenderNode(route, dispatch);
            if (page is FrameworkElement fe) fe.Tag = routeKey;
            pivot.Items.Add(new PivotItem { Header = route.TryGetProperty("title", out var t) ? t.GetString() : routeKey, Content = page });
        }
        if (pivot.Items.FirstOrDefault(i => ((PivotItem)i).Content is FrameworkElement f && f.Tag as string == active) is PivotItem activeItem)
            pivot.SelectedItem = activeItem;
        return pivot;
    }

    // ---- Route (with safe area) ---------------------------------------------

    private static UIElement RenderRoute(JsonElement node, Action<string> dispatch)
    {
        var respectSafeArea = !node.TryGetProperty("respect_safe_area", out var rsa) || rsa.GetBoolean();
        var grid = new Grid();
        grid.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        grid.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });

        var title = new TextBlock
        {
            Text = node.GetProperty("title").GetString(),
            Style = Application.Current.Resources["TitleTextBlockStyle"] as Style,
            Margin = respectSafeArea ? new Thickness(24, 16, 24, 16) : new Thickness(0, 0, 0, 0),
        };
        grid.Children.Add(title);

        var content = new ScrollViewer { Content = RenderChildren(node, dispatch) };
        if (!respectSafeArea) content.Margin = new Thickness(0);
        Grid.SetRow(content, 1);
        grid.Children.Add(content);
        return grid;
    }

    // ---- Platform view -------------------------------------------------------

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

    // ---- Stack / Form --------------------------------------------------------

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

    // ---- List ----------------------------------------------------------------

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

    // ---- Loading -------------------------------------------------------------

    private static UIElement RenderLoading(JsonElement node)
    {
        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        panel.Children.Add(new ProgressRing { IsActive = true, Width = 24, Height = 24 });
        if (node.TryGetProperty("label", out var label) && label.ValueKind == JsonValueKind.String)
            panel.Children.Add(new TextBlock { Text = label.GetString() });
        return panel;
    }

    // ---- Text ----------------------------------------------------------------

    private static UIElement RenderText(JsonElement node)
    {
        var text = new TextBlock { Text = node.GetProperty("text").GetString(), TextWrapping = TextWrapping.Wrap };
        var styleName = node.TryGetProperty("style", out var s) ? s.GetString() : "body";
        text.Style = styleName switch
        {
            "display" => Application.Current.Resources["HeaderTextBlockStyle"] as Style,
            "headline" => Application.Current.Resources["SubheaderTextBlockStyle"] as Style,
            "title" => Application.Current.Resources["TitleTextBlockStyle"] as Style,
            "caption" or "footnote" => Application.Current.Resources["CaptionTextBlockStyle"] as Style,
            _ => Application.Current.Resources["BodyTextBlockStyle"] as Style,
        };
        if (node.TryGetProperty("semantics", out var sem)
            && sem.TryGetProperty("label", out var semLabel)
            && semLabel.ValueKind == JsonValueKind.String)
            AutomationProperties.SetName(text, semLabel.GetString());
        return text;
    }

    // ---- Input ---------------------------------------------------------------

    private static UIElement RenderInput(JsonElement node, Action<string> dispatch)
    {
        var semantics = node.GetProperty("semantics");
        var label = semantics.TryGetProperty("label", out var sl) && sl.ValueKind == JsonValueKind.String ? sl.GetString() : null;
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        var value = node.GetProperty("value").GetString() ?? "";
        var placeholder = node.TryGetProperty("placeholder", out var ph) && ph.ValueKind == JsonValueKind.String ? ph.GetString() : null;
        var enabled = semantics.GetProperty("enabled").GetBoolean();
        var inputType = node.TryGetProperty("input_type", out var it) ? it.GetString() : null;

        if (node.TryGetProperty("secure", out var secure) && secure.GetBoolean())
        {
            var pw = new PasswordBox { Password = value, Header = label, PlaceholderText = placeholder, IsEnabled = enabled };
            AutomationProperties.SetName(pw, label ?? placeholder ?? key);
            pw.PasswordChanged += (_, _) => dispatch(EventJson(key, action, pw.Password));
            return pw;
        }

        var textInput = new TextBox { Text = value, Header = label, PlaceholderText = placeholder, IsEnabled = enabled };
        textInput.TextChanged += (_, _) => dispatch(EventJson(key, action, textInput.Text));
        if (inputType != null)
        {
            var scope = new Microsoft.UI.Xaml.Input.InputScope();
            scope.Names.Add(new Microsoft.UI.Xaml.Input.InputScopeName(inputType switch
            {
                "email" => Microsoft.UI.Xaml.Input.InputScopeNameValue.EmailSmtpAddress,
                "number" => Microsoft.UI.Xaml.Input.InputScopeNameValue.Number,
                "phone" => Microsoft.UI.Xaml.Input.InputScopeNameValue.TelephoneNumber,
                "url" => Microsoft.UI.Xaml.Input.InputScopeNameValue.Url,
                _ => Microsoft.UI.Xaml.Input.InputScopeNameValue.Default,
            }));
            textInput.InputScope = scope;
        }
        AutomationProperties.SetName(textInput, label ?? placeholder ?? key);
        return textInput;
    }

    // ---- Button --------------------------------------------------------------

    private static UIElement RenderButton(JsonElement node, Action<string> dispatch)
    {
        var button = new Button
        {
            Content = node.GetProperty("label").GetString(),
            HorizontalAlignment = HorizontalAlignment.Stretch,
            IsEnabled = node.GetProperty("semantics").GetProperty("enabled").GetBoolean(),
        };
        var variant = node.TryGetProperty("variant", out var v) ? v.GetString() : "primary";
        if (variant == "destructive")
            button.Background = new SolidColorBrush(Microsoft.UI.Colors.IndianRed);
        else if (variant != "secondary" && Application.Current.Resources.TryGetValue("CrossUiPrimaryBrush", out var primary) && primary is Brush brush)
            button.Background = brush;
        AutomationProperties.SetName(button,
            node.GetProperty("semantics").TryGetProperty("label", out var lbl) ? lbl.GetString()
            : node.GetProperty("label").GetString());
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("action").GetString()!;
        button.Click += (_, _) => dispatch(EventJson(key, action));
        return button;
    }

    // ---- Toggle --------------------------------------------------------------

    private static UIElement RenderToggle(JsonElement node, Action<string> dispatch)
    {
        var semantics = node.GetProperty("semantics");
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        var isChecked = node.TryGetProperty("checked", out var chk) && chk.GetBoolean();
        var enabled = semantics.GetProperty("enabled").GetBoolean();
        var lbl = node.TryGetProperty("label", out var lb) && lb.ValueKind == JsonValueKind.String ? lb.GetString() : null;

        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 12 };
        var toggle = new ToggleSwitch { IsOn = isChecked, IsEnabled = enabled };
        toggle.Toggled += (_, _) => dispatch(EventJson(key, action, toggle.IsOn ? "true" : "false"));
        panel.Children.Add(toggle);
        if (lbl != null) panel.Children.Add(new TextBlock { Text = lbl, VerticalAlignment = VerticalAlignment.Center });
        AutomationProperties.SetName(panel, lbl ?? key);
        return panel;
    }

    // ---- Image ---------------------------------------------------------------

    private static UIElement RenderImage(JsonElement node)
    {
        var src = node.GetProperty("src").GetString();
        var alt = node.TryGetProperty("alt", out var a) && a.ValueKind == JsonValueKind.String ? a.GetString() : "";
        var image = new Image { Stretch = Stretch.Uniform };
        if (Uri.TryCreate(src, UriKind.Absolute, out var uri))
            image.Source = new BitmapImage(uri);
        AutomationProperties.SetName(image, alt);
        return image;
    }

    // ---- Slider --------------------------------------------------------------

    private static UIElement RenderSlider(JsonElement node, Action<string> dispatch)
    {
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        var value = node.GetProperty("value").GetDouble();
        var min = node.GetProperty("min").GetDouble();
        var max = node.GetProperty("max").GetDouble();
        var step = node.TryGetProperty("step", out var st) && st.ValueKind == JsonValueKind.Number ? st.GetDouble() : 1.0;
        var enabled = node.GetProperty("semantics").GetProperty("enabled").GetBoolean();

        var slider = new Slider
        {
            Value = value,
            Minimum = min,
            Maximum = max,
            StepFrequency = step,
            IsEnabled = enabled,
            HorizontalAlignment = HorizontalAlignment.Stretch,
        };
        slider.ValueChanged += (_, _) => dispatch(EventJson(key, action, slider.Value.ToString("G")));
        return slider;
    }

    // ---- Picker --------------------------------------------------------------

    private static UIElement RenderPicker(JsonElement node, Action<string> dispatch)
    {
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        var selected = node.GetProperty("selected").GetString() ?? "";
        var enabled = node.GetProperty("semantics").GetProperty("enabled").GetBoolean();
        var options = node.TryGetProperty("options", out var opts) ? opts.EnumerateArray().ToArray() : [];

        var combo = new ComboBox { IsEnabled = enabled, HorizontalAlignment = HorizontalAlignment.Stretch };
        foreach (var opt in options)
        {
            var item = new ComboBoxItem
            {
                Content = opt.GetProperty("label").GetString(),
                Tag = opt.GetProperty("value").GetString(),
            };
            if ((string?)item.Tag == selected) combo.SelectedItem = item;
            combo.Items.Add(item);
        }
        combo.SelectionChanged += (_, _) =>
        {
            if (combo.SelectedItem is ComboBoxItem sel)
                dispatch(EventJson(key, action, sel.Tag as string ?? ""));
        };
        return combo;
    }

    // ---- DatePicker ----------------------------------------------------------

    private static UIElement RenderDatePicker(JsonElement node, Action<string> dispatch)
    {
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        var enabled = node.GetProperty("semantics").GetProperty("enabled").GetBoolean();
        var mode = node.TryGetProperty("mode", out var md) ? md.GetString() : "datetime";

        DateTimeOffset? initial = null;
        if (node.TryGetProperty("value", out var val) && val.ValueKind == JsonValueKind.String
            && DateTimeOffset.TryParse(val.GetString(), out var parsed))
            initial = parsed;

        if (mode == "time")
        {
            var picker = new TimePicker
            {
                Time = initial?.TimeOfDay ?? TimeSpan.Zero,
                IsEnabled = enabled,
            };
            picker.TimeChanged += (_, _) =>
                dispatch(EventJson(key, action, picker.Time.ToString()));
            return picker;
        }

        var datePicker = new CalendarDatePicker
        {
            Date = initial?.Date,
            IsEnabled = enabled,
        };
        datePicker.DateChanged += (_, _) =>
            dispatch(EventJson(key, action, datePicker.Date?.ToString("O") ?? ""));
        return datePicker;
    }

    // ---- Dialog --------------------------------------------------------------

    private static UIElement RenderDialog(JsonElement node, Action<string> dispatch)
    {
        var key = node.GetProperty("key").GetString()!;
        var title = node.GetProperty("title").GetString();
        var confirmLabel = node.TryGetProperty("confirm_label", out var cl) ? cl.GetString() : null;
        var confirmAction = node.TryGetProperty("confirm_action", out var ca) ? ca.GetString() : null;
        var cancelLabel = node.TryGetProperty("cancel_label", out var dl) ? dl.GetString() : null;
        var cancelAction = node.TryGetProperty("cancel_action", out var da) ? da.GetString() : null;

        var dialog = new ContentDialog
        {
            Title = title,
            CloseButtonText = cancelLabel,
            PrimaryButtonText = confirmLabel,
            DefaultButton = ContentDialogButton.Primary,
        };
        var body = Children(node).FirstOrDefault();
        if (body.ValueKind != JsonValueKind.Undefined && body.TryGetProperty("type", out var bt) && bt.GetString() == "text")
            dialog.Content = body.GetProperty("text").GetString();
        dialog.PrimaryButtonClick += (_, _) => { if (confirmAction != null) dispatch(EventJson(key, confirmAction)); };
        dialog.CloseButtonClick += (_, _) => { if (cancelAction != null) dispatch(EventJson(key, cancelAction)); };

        _ = dialog.ShowAsync();
        return new TextBlock { Visibility = Visibility.Collapsed };
    }

    // ---- Helpers -------------------------------------------------------------

    private static UIElement RenderCheckbox(JsonElement node, Action<string> dispatch)
    {
        var key = node.GetProperty("key").GetString()!;
        var action = node.GetProperty("on_change").GetString()!;
        var isChecked = node.TryGetProperty("checked", out var chk) && chk.GetBoolean();
        var enabled = node.GetProperty("semantics").GetProperty("enabled").GetBoolean();
        var lbl = node.TryGetProperty("label", out var lb) && lb.ValueKind == JsonValueKind.String ? lb.GetString() : null;

        var checkbox = new CheckBox { Content = lbl, IsChecked = isChecked, IsEnabled = enabled };
        checkbox.Checked += (_, _) => dispatch(EventJson(key, action, "true"));
        checkbox.Unchecked += (_, _) => dispatch(EventJson(key, action, "false"));
        return checkbox;
    }

    private static UIElement RenderDivider(JsonElement node)
    {
        return new Border
        {
            Height = 1,
            Background = (Brush)Application.Current.Resources["SystemControlForegroundBaseLowBrush"],
            Margin = new Thickness(0, 8, 0, 8),
        };
    }

    private static UIElement RenderCard(JsonElement node, Action<string> dispatch)
    {
        var border = new Border
        {
            CornerRadius = new CornerRadius(8),
            Padding = new Thickness(16),
            Background = (Brush)Application.Current.Resources["CardBackgroundFillColorDefaultBrush"],
            Child = RenderChildren(node, dispatch),
        };
        return border;
    }

    private static UIElement RenderChip(JsonElement node, Action<string> dispatch)
    {
        var key = node.GetProperty("key").GetString()!;
        var label = node.GetProperty("label").GetString();
        var hasDismiss = node.TryGetProperty("on_dismiss", out var dismiss) && dismiss.ValueKind == JsonValueKind.String;

        var panel = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 4 };
        var chip = new Border
        {
            CornerRadius = new CornerRadius(12),
            Padding = new Thickness(12, 4, 12, 4),
            Background = (Brush)Application.Current.Resources["AccentFillColorDefaultBrush"],
            Child = new TextBlock { Text = label },
        };
        panel.Children.Add(chip);

        if (hasDismiss)
        {
            var closeBtn = new Button { Content = new SymbolIcon(Symbol.Cancel), Width = 24, Height = 24 };
            closeBtn.Click += (_, _) => dispatch(EventJson(key, dismiss.GetString()!));
            panel.Children.Add(closeBtn);
        }
        return panel;
    }

    // ---- Helpers (cont.)

    private static StackPanel RenderChildren(JsonElement node, Action<string> dispatch, Orientation orientation = Orientation.Vertical)
    {
        var panel = new StackPanel { Spacing = 16, Orientation = orientation };
        foreach (var child in Children(node)) panel.Children.Add(RenderNode(child, dispatch));
        return panel;
    }

    private static JsonElement[] Children(JsonElement node) =>
        node.TryGetProperty("children", out var children) ? children.EnumerateArray().ToArray() : [];

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
        var enabled = node.TryGetProperty("semantics", out var sem)
            && sem.TryGetProperty("enabled", out var se) && se.GetBoolean();

        return (type, element) switch
        {
            ("text", TextBlock tb) => Update(tb, _ => tb.Text = node.GetProperty("text").GetString()),
            ("button", Button bt) => Update(bt, _ => { bt.Content = node.GetProperty("label").GetString(); bt.IsEnabled = enabled; }),
            ("input", TextBox tb) => Update(tb, _ => { var v = node.GetProperty("value").GetString() ?? ""; if (tb.Text != v) tb.Text = v; tb.IsEnabled = enabled; }),
            ("input", PasswordBox pb) => Update(pb, _ => { var v = node.GetProperty("value").GetString() ?? ""; if (pb.Password != v) pb.Password = v; pb.IsEnabled = enabled; }),
            ("toggle", ToggleSwitch ts) => Update(ts, _ => { ts.IsOn = node.TryGetProperty("checked", out var c) && c.GetBoolean(); ts.IsEnabled = enabled; }),
            ("slider", Slider sl) => Update(sl, _ => { sl.Value = node.GetProperty("value").GetDouble(); sl.IsEnabled = enabled; }),
            ("image", Image img) => Update(img, _ =>
            {
                if (node.TryGetProperty("src", out var s) && Uri.TryCreate(s.GetString(), UriKind.Absolute, out var uri))
                    img.Source = new BitmapImage(uri);
            }),
            _ => false,
        };
    }

    private static bool Update<T>(T element, Action<T> apply) where T : UIElement
    {
        apply(element);
        return true;
    }

    private static double Spacing(JsonElement node) =>
        node.TryGetProperty("spacing", out var sp) && sp.ValueKind == JsonValueKind.String
            ? sp.GetString() switch { "spacing.sm" => 8, "spacing.lg" => 24, _ => 16 }
            : 16;

    private static string EventJson(string nodeKey, string action, string? value = null)
    {
        var payload = new JsonObject { ["node_key"] = nodeKey, ["action"] = new JsonObject { ["type"] = action } };
        if (value is not null) payload["action"]!["value"] = value;
        return payload.ToJsonString();
    }
}
