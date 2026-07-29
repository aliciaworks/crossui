# Weblate

Weblate is open source and normally synchronizes directly with Git. Create one
component per native format and point all components at the same repository:

| Component | File mask | Monolingual base file | Format |
| --- | --- | --- | --- |
| Android | `localization/android/values-*/crossui_strings.xml` | `localization/android/values/crossui_strings.xml` | Android String Resource |
| Windows | `localization/windows/Strings/*/Resources.resw` | `localization/windows/Strings/en-US/Resources.resw` | .NET Resource |

Enable translation propagation when identical keys should share translations
between components. Configure a push branch so Weblate changes arrive through a
reviewable pull request.

Current Weblate documentation lists Apple `.strings`, but not `.xcstrings`, as
its native Apple format. Keep CrossUI's `.xcstrings` output for Xcode and use an
XLIFF bridge or a separately verified Weblate add-on before enabling Apple
catalog synchronization. Do not silently convert the catalog in the application
runtime.

Use the sibling `verify-generated-resources.yml` in CI. No Weblate SDK, token,
or API client is needed in the application.

References:

- [Weblate multi-platform projects](https://docs.weblate.org/en/latest/faq.html#how-to-translate-multi-platform-projects)
- [Weblate Android resources](https://docs.weblate.org/en/latest/formats/android.html)
- [Weblate .NET resources](https://docs.weblate.org/en/latest/formats/resx.html)
- [Weblate Apple formats](https://docs.weblate.org/en/latest/formats/apple.html)
