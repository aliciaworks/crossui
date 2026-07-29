# Crowdin

Copy `crowdin.yml` to a suitable location and update `base_path`, the source
locale, and module paths. Copy `github-action.yml` into `.github/workflows`.

The checked-in configuration covers the monolingual Android and Windows files.
Configure `localization/apple/Localizable.xcstrings` as an Apple String Catalog
in Crowdin and export it back to the same path. String Catalogs contain all
languages in one file, so they do not use the per-language path placeholders
shown for Android and Windows.

Required repository secrets:

- `CROWDIN_PROJECT_ID`
- `CROWDIN_PERSONAL_TOKEN`

The workflow uses the repository `GITHUB_TOKEN` to create the translation pull
request. Pin actions to reviewed commit SHAs when adopting the template in a
production repository.

References:

- [Crowdin GitHub Action](https://github.com/crowdin/github-action)
- [Crowdin configuration file](https://support.crowdin.com/developer/configuration-file/)
- [Crowdin supported formats](https://support.crowdin.com/supported-formats/)
