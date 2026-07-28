# WinUI 3 host

CrossUI emits a normal `.xaml` control and its `.xaml.cs` companion.
`MainWindow` hosts the generated control directly; no native DLL, C ABI, JSON
decoder, or runtime renderer is involved. The generated code-behind supplies
event handlers and an `INotifyPropertyChanged` state adapter for typed
documents.

Generate the XAML, then build:

```powershell
.\gradlew.bat generateNativeUi
dotnet build .\hosts\windows\CrossUi.Windows.csproj
```

Connect actions at construction time:

```csharp
var screen = new SettingsScreen((action, value) =>
{
    sharedFeature.Dispatch(action, value);
});
```

Apply state snapshots from the KMP bridge without dispatching a second action:

```csharp
screen.State.ApplyEmail(snapshot.Email);
screen.State.ApplyDarkMode(snapshot.DarkMode);
```

User edits update the generated property and dispatch its DSL event. Host
snapshots use the generated `Apply<Property>` methods, which raise
`PropertyChanged` but do not feed the change back into the reducer. This seam is
deliberate: Kotlin/Native does not directly export KMP classes as .NET types, so
an existing Windows app keeps its current interop layer while generated WinUI
remains ordinary compiled XAML and C#.

`CrossUiTypedFixture.xaml` is not a runtime sample screen. It is a checked-in
compile fixture covering two-way state, visibility, enabled state, action
dispatch, and the generated observation adapter.
