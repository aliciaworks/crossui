using CrossUi.Generated;
using System;
using System.Globalization;
using System.Threading.Tasks;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Media;
using Microsoft.Windows.Globalization;
using Windows.Storage.Pickers;
using WinRT.Interop;

namespace CrossUi.Windows;

public sealed partial class MainWindow : Window
{
    private readonly CrossUiShowcase showcase;

    public MainWindow()
    {
        InitializeComponent();
        SystemBackdrop = new MicaBackdrop();
        showcase = new CrossUiShowcase(Dispatch);
        Root.Children.Add(showcase);
    }

    private void Dispatch(string action, string? value)
    {
        switch (action)
        {
            case "volume_changed" when double.TryParse(
                value,
                NumberStyles.Float,
                CultureInfo.InvariantCulture,
                out var volume):
                showcase.State.ApplyVolumeLabel($"Volume: {volume:P0}");
                break;
            case "language_changed" when !string.IsNullOrWhiteSpace(value):
                ApplicationLanguages.PrimaryLanguageOverride = value;
                showcase.RefreshLocalization(value);
                showcase.State.ApplyPickerStatus($"Language: {value}");
                break;
            case "dark_mode_changed" when bool.TryParse(value, out var darkMode):
                var requestedTheme = darkMode
                    ? ElementTheme.Dark
                    : ElementTheme.Light;
                Root.RequestedTheme = requestedTheme;
                showcase.RequestedTheme = requestedTheme;
                SystemBackdrop = new MicaBackdrop();
                break;
            case "pick_attachment":
                StartPicking("Select a PDF document", false, ".pdf");
                break;
            case "pick_photos":
                StartPicking("Choose photos", true, ".jpg", ".jpeg", ".png", ".mp4");
                break;
        }
    }

    private void StartPicking(string title, bool multiple, params string[] fileTypes) =>
        _ = PickFilesSafelyAsync(title, multiple, fileTypes);

    private async Task PickFilesSafelyAsync(
        string title,
        bool multiple,
        params string[] fileTypes
    )
    {
        try
        {
            await PickFilesAsync(title, multiple, fileTypes);
        }
        catch (Exception exception)
        {
            showcase.State.ApplyPickerStatus($"Picker failed: {exception.Message}");
        }
    }

    private async Task PickFilesAsync(string title, bool multiple, params string[] fileTypes)
    {
        var picker = new FileOpenPicker
        {
            SuggestedStartLocation = PickerLocationId.DocumentsLibrary,
        };
        picker.FileTypeFilter.Clear();
        foreach (var fileType in fileTypes)
        {
            picker.FileTypeFilter.Add(fileType);
        }
        InitializeWithWindow.Initialize(picker, WindowNative.GetWindowHandle(this));

        if (multiple)
        {
            var files = await picker.PickMultipleFilesAsync();
            showcase.State.ApplyPickerStatus(
                files.Count == 0 ? "Selection cancelled" : $"Selected {files.Count} file(s)"
            );
            return;
        }

        var file = await picker.PickSingleFileAsync();
        showcase.State.ApplyPickerStatus(
            file is null ? "Selection cancelled" : $"Selected: {file.Name}"
        );
    }
}
