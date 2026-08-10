# Component catalog

This table documents the stable CrossUI IR components as of v2.3.
`native` means the host uses its platform control directly. `adapted`
preserves the semantic contract using a different native composition.
`unsupported` is visible to the caller; it never silently becomes another
component.

| Component | v2.2 | iOS SwiftUI | Android Compose | Windows WinUI 3 |
|-----------|------|-------------|-----------------|-----------------|
| Text (6 styles) | ✅ | native — `.font(.largeTitle/.title/.title2/.body/.caption/.footnote)` | native — `Text(fontSize=)` | native — `TextBlock HeaderTextBlockStyle/…` |
| Button (3 variants) | ✅ | native — `Button(role:)` / `.tint()` | native — `Button` / `OutlinedButton` | native — `Button` with background brush |
| Input (6 keyboard types) | ✅ | native — `SecureField`/`TextField` + `UIKeyboardType` | native — `OutlinedTextField` + `KeyboardOptions` | native — `TextBox`/`PasswordBox` + `InputScope` |
| Stack (H/V) | ✅ | native — `HStack`/`VStack` | native — `Row`/`Column` | native — `StackPanel` |
| List | ✅ | native — `VStack`+`ForEach` w/ select | native — `LazyColumn` w/ `TextButton` | native — `StackPanel`+`Button` wrapper |
| Form | ✅ | native — `VStack(.leading, 12)` | adapted — `Column(24.dp)` | native — `StackPanel` |
| Loading | ✅ | native — `ProgressView` | native — `CircularProgressIndicator` | native — `ProgressRing` |
| Navigation (Tab/Stack) | ✅ v2.1 | native — `TabView` / `NavigationStack` | native — `NavigationBar` / back stack | native — `Pivot` / direct render |
| Route (Safe Area) | ✅ v2.1 | native — `NavigationStack` + `.ignoresSafeArea()` | native — `TopAppBar` + `systemBarsPadding()` | native — `Grid`+`ScrollViewer` w/ margin |
| Toggle | ✅ v2.0 | native — `Toggle` | native — `Switch`+`Row` | native — `ToggleSwitch` |
| Image | ✅ v2.0 | native — `AsyncImage` | native — Coil `AsyncImage` | native — `BitmapImage` |
| Dialog | ✅ v2.0 | native — `.alert` | native — `AlertDialog` | native — `ContentDialog` |
| Slider | ✅ v2.1 | native — `Slider(value:in:step:)` | native — `Slider(valueRange=)` | native — `Slider` |
| Picker | ✅ v2.1 | native — `Picker(.menu)` | native — `DropdownMenu` | native — `ComboBox` |
| DatePicker | ✅ v2.1 | native — `DatePicker` | native — Material 3 `DatePicker` | native — `CalendarDatePicker`/`TimePicker` |
| Checkbox | ✅ v2.2 | adapted — `Button` + SF Symbol | native — `Checkbox` | native — `CheckBox` |
| Divider | ✅ v2.2 | native — `Divider()` | native — `HorizontalDivider()` | native — `Border(1px)` |
| Card | ✅ v2.2 | native — `VStack` + `.regularMaterial` | native — `ElevatedCard` | native — `Border` w/ card brush |
| Chip | ✅ v2.2 | adapted — `Capsule` + optional ✕ | native — `InputChip`/`SuggestionChip` | adapted — `Border` + optional `SymbolIcon` |
| File/Media picker | ✅ v2.3 | semantic button → host `fileImporter`/`PhotosPicker` effect | semantic button → host Activity Result effect | semantic button → host picker effect |
| Motion (presence) | ✅ v2.4 | native — `.transition` + spring `.animation(value:)` | native — `AnimatedVisibility` enter/exit | native — `Border` + `ThemeTransition` |
| PlatformView | ✅ | compile-time error without escape hatch | compile-time error without escape hatch | compile-time error without escape hatch |

`PlatformView` is intentionally outside the portable component set. Use a
target platform, a host-owned name, a typed payload, and an advertised
capability. Source generation fails until the target backend is given an
explicit native escape hatch.

## Navigation modes

| Mode | Description | iOS | Android | WinUI |
|------|-------------|-----|---------|-------|
| `tab` (default) | Peer routes as tab bar items | `TabView` with `.tabItem` | `NavigationBar` (bottom) | `Pivot` |
| `stack` | LIFO push-pop with back behaviour | `NavigationStack` | implicit back stack | direct route render |

## Text styles

| Style | Apple SF Pro | Material 3 | WinUI Fluent |
|-------|-------------|------------|--------------|
| `display` | Large Title | DisplayLarge | HeaderTextBlockStyle |
| `headline` | Title 1 | HeadlineLarge | SubheaderTextBlockStyle |
| `title` | Title 2 | TitleLarge | TitleTextBlockStyle |
| `body` (default) | Body | BodyLarge | BodyTextBlockStyle |
| `caption` | Caption 1 | LabelLarge | CaptionTextBlockStyle |
| `footnote` | Caption 2 | LabelSmall | CaptionTextBlockStyle |

## Input types

| Type | iOS `UIKeyboardType` | Android `inputType` | WinUI `InputScopeNameValue` |
|------|---------------------|---------------------|-----------------------------|
| `text` (default) | default | text | Default |
| `email` | emailAddress | textEmailAddress | EmailSmtpAddress |
| `number` | decimalPad | numberDecimal | Number |
| `phone` | phonePad | phone | TelephoneNumber |
| `url` | URL | textUri | Url |
| `password` | default + `isSecureTextEntry` | textPassword | Password |

## Return key

| Key | iOS `UIReturnKeyType` | Android `ImeAction` | WinUI |
|-----|----------------------|---------------------|-------|
| `done` | `.done` | `ImeAction.Done` | Default |
| `go` | `.go` | `ImeAction.Go` | n/a |
| `search` | `.search` | `ImeAction.Search` | n/a |
| `send` | `.send` | `ImeAction.Send` | n/a |
| `next` | `.next` | `ImeAction.Next` | n/a |
