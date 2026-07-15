# WinUI 3 host verification

This standalone, unpackaged WinUI 3 project renders the same login contract used
by the Rust showcase and the Android Compose host. It intentionally uses native
Windows App SDK controls: `TextBox`, `PasswordBox`, `Button`, `ProgressRing`,
and `TextBlock`. Semantic labels are mapped through WinUI automation properties.
For leaf-only keyed patches (for example, an input value change), the renderer
updates the existing native control in place. Navigation and structural patches
intentionally rebuild the view tree, which keeps the fallback path predictable.

Register a target-only view before rendering a document that contains a
`PlatformView`:

```csharp
CrossUiRenderer.RegisterPlatformView("map", payload => new MyMapControl(payload));
```

Only `platform: "windows"` nodes use this registration. Other platforms and
unknown names remain visible diagnostics.

Build it from this directory with:

```powershell
dotnet build
```
