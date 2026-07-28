# Android host

`generated/CrossUiShowcase.kt` is Jetpack Compose source emitted during the
Gradle build. Add it to an Android application's source set and pass an event
dispatcher to `CrossUiShowcase`.

There is no JSON decoder or dynamic UI renderer in the Android application.
Regenerate the file from the repository root:

```powershell
.\gradlew.bat generateNativeUi
```
