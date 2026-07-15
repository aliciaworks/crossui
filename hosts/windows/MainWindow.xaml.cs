using System;
using System.Runtime.InteropServices;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;

namespace CrossUi.Windows;

public sealed partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        SystemBackdrop = new MicaBackdrop();
        Render(CrossUiNative.InitialDocument());
    }

    private void Render(string document)
    {
        Root.Children.Clear();
        Root.Children.Add(CrossUiRenderer.RenderDocument(document, Dispatch));
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
