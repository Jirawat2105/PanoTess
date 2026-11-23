# PanoTess for Android

Version 1.0.1 centers the empty preview message horizontally and vertically in the preview area.

Native Android edition targeting Android 17 / API 37, minimum Android 10 / API 29. It uses the Android image decoder rather than Python/Qt. No network or broad storage permission is requested.

Select JPG/PNG images, choose 2/3/4 slices and a per-slice ratio, choose a destination folder, and export. Each input gets a separate uniquely named subfolder. Android's document picker grants access to the selected output folder; automatic writes beside an arbitrary gallery image are not available. Fixed ratios use a centered crop.

PNG exports decoded 8-bit pixels without resampling. JPG uses quality 100 and is re-encoded. This edition does not preserve source JPEG quantization tables, EXIF metadata, or guarantee original ICC metadata. TIFF, RAW, HDR/high-bit-depth images and animated images are outside this edition's supported scope. Large images are rejected when they exceed the phone's memory budget. Unlike the desktop edition, crop positioning is currently centered. Keep the app in the foreground during export; leaving or closing it cancels incomplete work when the activity is destroyed. Finished folders remain saved. Errors are stored in the app's private files and can be viewed through View logs.

The APK is signed with a local **testing** key. It is suitable for sideload testing, not a Google Play production release. The private key is stored under `.git/android-testing.p12` and is excluded from source control. This is a pure Java APK with no native libraries, so it is CPU-architecture independent.

## Build on Windows

Requires JDK 21+, Python 3, Android SDK platform `platforms;android-37.0` and build tools `build-tools;37.0.0`. Install those SDK components with `sdkmanager`. SDK location defaults to `%LOCALAPPDATA%/Android/Sdk`; override it with `ANDROID_HOME`.

Run `py -3 android/build.py` from the repository root or double-click `android/Build APK.bat`. The APK and SHA256 checksum are placed in `dist/android/`. A fresh clone must be a Git checkout because the testing key is placed under `.git/`.

No device is installed or modified by the build script. Run `adb install -r dist/android/PanoTess-Android17-testing.apk` yourself with a connected testing device to install.

## Verification

Verified on an Android 17 / API 37 x86_64 emulator: native activity launch, preview loading, PNG pixel comparison against the source across all slices, exact centered crop, unique output folders, JPG decoding and manifest creation. APK signature and target SDK are verified by the build script. Physical ARM devices and manual system-picker interaction have not been tested.

`py -3 android/test_device.py` builds a separate test-only documents provider and instrumentation APK and runs those checks on `emulator-5580`. The test runner uses instrumentation shell identity to access its private document provider; the production APK has neither that provider nor any broad storage permission. Create/start the dedicated API 37 emulator before running this command.
