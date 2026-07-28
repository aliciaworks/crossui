# WinUI 3 host

The generated `CrossUiShowcase.xaml` file is compiled as normal WinUI markup.
`MainWindow` hosts that generated control directly; no native DLL, C ABI, JSON
decoder, or runtime renderer is involved.

Generate the XAML, then build:

```powershell
.\gradlew.bat generateNativeUi
dotnet build .\hosts\windows\CrossUi.Windows.csproj
```

Event handlers are intentionally an integration seam: bind generated `Tag`
values to the application's KMP-backed state dispatcher in the host layer.
