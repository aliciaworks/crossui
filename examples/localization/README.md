# Localization CI examples

CrossUI generates native source resources; translation management remains a
Git/CI concern. Copy the relevant templates into the consuming repository and
replace `:shared-ui` with the module that applies the CrossUI plugin.

The recommended flow is:

1. `generateLocalizationSources` merges source keys.
2. `verifyLocalization` validates the source resources.
3. CI checks that `localization/` has no uncommitted source changes.
4. Crowdin or Weblate reads and writes translations through a branch or pull
   request.
5. The application builds native resources without a TMS runtime SDK.

`verify-generated-resources.yml` is vendor-neutral. The Crowdin directory adds
an official Crowdin GitHub Action template. Weblate normally uses its built-in
Git integration, so its directory documents component settings instead of
putting a Weblate token in the application or build.

