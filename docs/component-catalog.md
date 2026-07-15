# Component catalog

This table documents the stable CrossUI IR components. `native` means the host
uses its platform control directly. `adapted` preserves the semantic contract
using a different native composition. `unsupported` is visible to the caller;
it never silently becomes another component.

| Component | iOS SwiftUI | Android Compose | Windows WinUI 3 |
| --- | --- | --- | --- |
| Text | native | native | native |
| Button | native | native | native |
| Input / secure input | native | native | native |
| Stack | native | native | native |
| List | native | native | native |
| Form | native | adapted | native |
| Loading | native | native | native |
| Navigation / Route | native | native | native |
| PlatformView | unsupported without a host registration | unsupported without a host registration | unsupported without a host registration |

`PlatformView` is intentionally outside the portable component set. Use a
target platform, a host-owned name, a JSON payload, and an advertised
capability. The generic renderers show an explicit diagnostic until a target
application registers an implementation (WinUI) or injects a rendering closure
(SwiftUI).
