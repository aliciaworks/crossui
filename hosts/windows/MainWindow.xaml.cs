using System;
using System.Runtime.InteropServices;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace CrossUi.Windows;

public sealed partial class MainWindow : Window
{
    private readonly Grid _content = new();
    private readonly Grid _notifications = new();

    public MainWindow()
    {
        InitializeComponent();
        SystemBackdrop = new MicaBackdrop();
        Root.Children.Add(_content);
        Root.Children.Add(_notifications);
        Render(CrossUiNative.InitialDocument());
    }

    private void Render(string document)
    {
        if (!CrossUiRenderer.TryApplyPatch(_content, document))
        {
            _content.Children.Clear();
            _content.Children.Add(CrossUiRenderer.RenderDocument(document, Dispatch));
        }

        _notifications.Children.Clear();
        foreach (var message in CrossUiRenderer.NotificationMessages(document))
        {
            var notification = new Border
            {
                Background = new SolidColorBrush(Microsoft.UI.Colors.DodgerBlue),
                CornerRadius = new CornerRadius(8),
                Padding = new Thickness(16, 10, 16, 10),
                Margin = new Thickness(16),
                VerticalAlignment = VerticalAlignment.Top,
                HorizontalAlignment = HorizontalAlignment.Right,
                Child = new TextBlock { Text = message, Foreground = new SolidColorBrush(Microsoft.UI.Colors.White) },
            };
            _notifications.Children.Add(notification);
        }
    }

    private void Dispatch(string eventJson)
    {
        Render(CrossUiNative.DispatchEvent(eventJson));
    }
}

internal static class CrossUiNative
{
    [DllImport("crossui_ffi", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr crossui_initial_document();

    [DllImport("crossui_ffi", CallingConvention = CallingConvention.Cdecl)]
    private static extern IntPtr crossui_dispatch_event([MarshalAs(UnmanagedType.LPUTF8Str)] string eventJson);

    [DllImport("crossui_ffi", CallingConvention = CallingConvention.Cdecl)]
    private static extern void crossui_string_free(IntPtr value);

    public static string InitialDocument() => Read(crossui_initial_document());

    public static string DispatchEvent(string eventJson) => Read(crossui_dispatch_event(eventJson));

    private static string Read(IntPtr value)
    {
        if (value == IntPtr.Zero) throw new InvalidOperationException("CrossUI returned a null response.");
        try { return Marshal.PtrToStringUTF8(value) ?? throw new InvalidOperationException("CrossUI returned invalid UTF-8."); }
        finally { crossui_string_free(value); }
    }
}
