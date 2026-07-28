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

## Native resource mode

Native mode is the default.

- SwiftUI emits `String(localized:defaultValue:)`. `namespace` selects the
  string catalog table.
- Compose emits `stringResource(<resource-class>.string.<normalized-key>)`.
- WinUI emits one-time `x:Bind` properties backed by Windows App SDK
  `ResourceLoader`, with fallback handling.

Configure the Android resource class in an existing KMP project:

```kotlin
crossui {
    androidResourceClass.set("com.example.shared.R")
}
```

Android resource identifiers replace punctuation with underscores. For example,
`home.welcome` becomes `home_welcome`. A namespace is prefixed to the key.
CrossUI references resources owned by the existing project; it does not copy or
replace the project's XML, string catalogs, or `.resw` files.

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
