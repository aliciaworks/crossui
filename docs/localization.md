# Localization

CrossUI unifies localization identity and fallback semantics without forcing an
existing project to replace its resource system.

## Authoring

Ordinary strings remain supported:

```kotlin
text("welcome", "Welcome")
```

Use `localized` when a value belongs to the project's localization catalog:

```kotlin
text(
    "welcome",
    localized(
        key = "home.welcome",
        fallback = "Welcome",
    ),
)

button(
    "continue",
    localized("login.continue", "Continue"),
    event(LoginAction.Submit),
)
```

The fallback remains in semantic IR, diagnostics, previews, and platforms where
the resource is unavailable. Resource identity is preserved separately from the
fallback, so generated code never parses a decorated string at runtime.

Common text, button, route, loading, toggle, checkbox, chip, and picker-option
APIs accept `LocalizedText`. Less common fields can be attached explicitly:

```kotlin
emailInput(
    "email",
    bind(LoginState::email),
    "Email",
    "email_changed",
).localized(
    LocalizedField.Placeholder,
    localized("login.email.placeholder", "Email"),
)
```

## Native resource lookup

- SwiftUI emits `String(localized:defaultValue:)`. `namespace` selects the
  string catalog table.
- Compose emits `stringResource(<resource-class>.string.<normalized-key>)`.
- WinUI emits `x:Bind` properties backed by an explicit Windows App SDK MRT
  Core `ResourceContext`, with fallback handling. Pass the selected BCP-47 tag
  to the generated `RefreshLocalization(languageTag)` method. This refreshes
  the current control immediately, including unpackaged applications.

Configure the Android resource class in an existing KMP project:

```kotlin
crossui {
    androidResourceClass.set("com.example.shared.R")
}
```

Android resource identifiers replace punctuation with underscores. For example,
`home.welcome` becomes `home_welcome`. A namespace is prefixed to the key.

Android resource reads react to configuration changes during recomposition.
The generated Android connector uses lifecycle-aware StateFlow collection.
SwiftUI resource reads participate in SwiftUI locale invalidation. On WinUI,
change the language through the host and then refresh the generated bindings:

```csharp
Microsoft.Windows.Globalization.ApplicationLanguages.PrimaryLanguageOverride =
    "zh-CN";
view.RefreshLocalization("zh-CN");
```

Native WinUI lookup failures return the IR fallback and invoke the generated
`LocalizationError` diagnostic callback when one is installed.

## Source resource generation

CrossUI can extract localized keys at build time and merge them into native
source-language resources:

- Apple: `apple/Localizable.xcstrings`
- Android: `android/values/crossui_strings.xml`
- Windows: `windows/Strings/<source-locale>/Resources.resw`

Enable source generation explicitly:

```kotlin
import dev.crossui.gradle.LocalizationMode

crossui {
    localization {
        mode.set(LocalizationMode.Generated)
        sourceLocale.set("en-US")
        outputDirectory.set(layout.projectDirectory.dir("localization"))
    }
}
```

Run `./gradlew generateLocalizationSources` to merge new keys and update source
fallbacks. Existing keys not owned by CrossUI remain in the file. Apple
localizations and Android or Windows target-locale files are never overwritten.
This makes checked-in resources safe inputs and outputs for Crowdin or Weblate.

Run `./gradlew verifyLocalization` in CI. It detects:

- the same source key declared with conflicting fallbacks;
- missing generated source keys;
- duplicate Android or Windows resource keys;
- invalid configured, Apple, or Windows BCP-47 language tags.

`verifyCrossUi` also runs localization verification but does not repair missing
resources first. Normal assembly runs source generation. Generation is
intentionally not build-cacheable because it merges files that can contain
human or TMS-authored translations.

## Existing resource projects

External mode is the default, so adopting the plugin does not modify a project's
catalogs:

```kotlin
crossui {
    localization {
        mode.set(LocalizationMode.External)
        sourceLocale.set("en-US")
    }
}
```

External mode validates IR keys and fallbacks but neither requires nor writes
resource files. The generated platform source still references the project's
native resources. Use `Disabled` only when localization validation should also
be skipped.

For generated mode, commit the `localization/` directory when a TMS synchronizes
it through Git. Do not add a Crowdin/Weblate mobile SDK to the application:
translation upload, download, review, and pull requests belong in Git or CI.
See `examples/localization` for Crowdin and Weblate setup templates.

## Custom resolver mode

Projects with remote catalogs, generated accessors, or an existing localization
framework can register source templates:

```kotlin
crossui {
    localizationResolvers.put(
        "swiftui",
        "AppStrings.resolve(\"{{key}}\", fallback: \"{{fallback}}\")",
    )
    localizationResolvers.put(
        "compose",
        "AppStrings.resolve(\"{{key}}\", \"{{fallback}}\")",
    )
    localizationResolvers.put(
        "winui3",
        "AppStrings.Resolve(\"{{key}}\", \"{{fallback}}\")",
    )
}
```

Available placeholders are `{{key}}`, `{{fallback}}`, and `{{namespace}}`.
Templates are embedded during source generation and must evaluate to the
platform's string type. There is no runtime CrossUI resolver or UI interpreter.

The compiler API offers the same configuration:

```kotlin
val localization = LocalizationRegistry.build {
    androidResources("com.example.shared.R")
    register(
        ExportTarget.SwiftUi,
        "AppStrings.resolve(\"{{key}}\", fallback: \"{{fallback}}\")",
    )
}
```

## Existing project adoption

Generated and handwritten UI can use the same keys and native catalogs. A
project may configure native Android resources, a custom Apple resolver, and
native WinUI resources independently. Migration can therefore happen one field
or one screen at a time.

Localized formatting arguments and plural rules are intentionally reserved for
the next IR addition. They require typed arguments and platform plural-category
validation rather than string interpolation hidden inside the fallback.
