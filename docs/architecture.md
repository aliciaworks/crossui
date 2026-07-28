# Architecture

CrossUI separates authoring, compilation, generated UI, and shared runtime.

```text
Authored Kotlin DSL
        |
        v
Semantic UI IR (KMP)
        |
        v
Validation + derived policies (Kotlin/JVM)
        |
        v
Platform lowering
  +-----+---------+
  |     |         |
Apple Android  Windows
  |     |         |
SwiftUI Compose  WinUI 3
```

## Build-time boundary

The compiler is an ordinary Kotlin/JVM program. Gradle invokes it after compiling
the DSL source and before packaging a native application. It does not depend on
Kotlin compiler internals and no compiler code is embedded into the application.

Generated source uses native controls. Platform navigation is lowered separately:
SwiftUI uses `TabView` or `NavigationStack`, Android uses Material navigation and
Compose layout, and Windows uses `NavigationView` and WinUI controls.

## Semantic IR

`ui-ir` stores product intent: stable node keys, component kind, accessibility
semantics, state values, actions, traits, theme tokens, and typed platform hints.
It does not store a native view tree.

Documents are versioned and validated. Keys must be unique and non-empty. Target
profiles keep cultural identity (Apple HIG, Material, Fluent) separate from
physical capabilities such as touch, pointer, keyboard, display class, and
multi-window support.

Platform extensions are typed and namespaced. A Windows extension on an iPhone
target is reported instead of silently changing behavior. Explicit
`PlatformView` nodes require a backend escape hatch and never pretend to be
portable.

## Legalization and lowering

The legalizer validates the IR, checks extensions, applies HIG rules, and derives
policies from semantic traits. For example, an irreversible critical action
requires confirmation. Derived policy is kept out of authored IR so it can vary
by target and compiler version.

Each generator receives a resolved document and emits deterministic text. Native
source is inspectable, reviewable, and can be compiled by the platform toolchain.

## KMP runtime

`runtime` contains only concepts that benefit from shared business logic:

- reducers and observable stores;
- state bindings and typed events;
- navigation state;
- environment and accessibility preferences;
- capability declarations;
- optional document diffs for tests and tooling.

It does not decode UI JSON or dynamically construct controls. Native UI calls
shared state/event APIs directly.

## Source generation contract

`CrossUiCompiler.generate(document, targets, typeName)` returns generated source
in memory. `CrossUiCompiler.write(...)` writes it to a build or host directory.
The CLI accepts serialized IR for integrations that cannot directly execute the
Kotlin DSL.

Generation rejects unresolved platform views. This makes escape hatches visible
at build time rather than producing an unsupported runtime placeholder.
