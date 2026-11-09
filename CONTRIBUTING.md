# Contributing to CastBay

Thanks for helping! Bug reports, device reports, translations, fixes and features are all welcome. English or Chinese are both fine in issues and discussions.

## Before you start

- **Questions and ideas**: [Discussions](https://github.com/weenas/castbay/discussions).
- **A bug**: a [problem report](https://github.com/weenas/castbay/issues/new?template=bug_report.yml). Uploading a report from the app (About → Diagnostics → Upload log) and giving its ID helps a lot.
- **A security issue**: report it privately; see [SECURITY.md](SECURITY.md).
- **A bigger change** (a new feature, a new dependency, a change to the protocol code): open an issue or discussion first, so we can agree on the approach before you spend time on it.

## Building and testing

[README.md](README.md) explains how to build the app ([Building from Source](README.md#building-from-source)) and the website ([Website](README.md#website)), and how to test without a phone with `tools/sim` ([Testing without a phone](README.md#testing-without-a-phone)).

Before opening a pull request:

- `./gradlew assembleDebug :app:testDebugUnitTest` passes; for the website, `npm run build` in `web/`.
- You tried the change on a device or an emulator: a remote (D-pad) and, where it matters, a touch screen. Screens should work on a 1080p TV (960×540 dp), a car display (about 1280×600 dp) and a tablet held upright.
- New text the app shows is in both `app/src/main/res/values/strings.xml` (English) and `values-zh/strings.xml` (Chinese). If you can only write one, say so in the pull request.
  The one exception is Stats for nerds (the playback statistics overlay): its labels ("decode … ms", "dropped …", source names) stay English on purpose, as in other players.

## Pull requests

- One topic per pull request, with a description of what changes for the person using CastBay and how you tested it.
- Commit messages in English: a short summary line, then why the change is needed.
- Follow the style of the code around your change: Kotlin with Jetpack Compose, comments that explain why rather than what.
- Don't change `versionName` / `versionCode`; they change only when a release is made.
- CI builds the app and the website; a maintainer merges once it passes and the change is reviewed. `main` only takes pull requests, merged by rebase (its history stays linear) after the `build`, `website-build` and GitGuardian checks pass; it is never force-pushed.

## Translations

The Android app is in English, Simplified Chinese and Traditional Chinese. Translations are done on [Hosted Weblate](https://hosted.weblate.org/engage/castbay/): sign in, pick a language (or start a new one) and translate in the browser. Weblate keeps the translations up to date as strings are added and sends them here as pull requests, which a maintainer merges once CI passes.

- Leave placeholders such as `%1$s` and `%2$d` as they are; their order in the sentence can change. Weblate and the unit tests (`TranslationsTest`) check them.
- Keep the product names AirPlay, DLNA, CastBay Link and CastBay as they are.
- A string not translated yet shows in English, so a partial translation is fine. Once a language is mostly done, it is added to the language list in Settings (`ReceiverSettings.LANGUAGES`, one line); until then the app uses it when it is the system language.

You can also edit `app/src/main/res/values-<code>/strings.xml` directly (for example `values-ja`, or `values-pt-rBR` for Brazilian Portuguese) and open a pull request; escape apostrophes (`\'`). CastBay Desktop is translated in its code for now (English and Chinese).

## License

CastBay is GPL-3.0. By contributing, you agree that your contribution is licensed under the same terms.
