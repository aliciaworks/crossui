param(
    [ValidateSet("arm64-v8a")]
    [string]$Abi = "arm64-v8a"
)

$ErrorActionPreference = "Stop"
$ndkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk\26.1.10909125"
$target = "aarch64-linux-android"
$toolchain = Join-Path $ndkRoot "toolchains\llvm\prebuilt\windows-x86_64\bin"

if (-not (Test-Path $toolchain)) {
    throw "Android NDK was not found at $ndkRoot"
}

$previousLinker = $env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER
$previousRustFlags = $env:RUSTFLAGS
try {
    $env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = Join-Path $toolchain "clang.exe"
    $env:RUSTFLAGS = "$env:RUSTFLAGS --codegen link-arg=--target=aarch64-linux-android24".Trim()
    cargo build -p crossui-android --target $target --release
}
finally {
    if ($null -eq $previousLinker) { Remove-Item Env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER -ErrorAction SilentlyContinue }
    else { $env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $previousLinker }
    if ($null -eq $previousRustFlags) { Remove-Item Env:RUSTFLAGS -ErrorAction SilentlyContinue }
    else { $env:RUSTFLAGS = $previousRustFlags }
}

$destination = Join-Path $PSScriptRoot "..\..\testandroidapp\app\src\main\jniLibs\$Abi"
New-Item -ItemType Directory -Force -Path $destination | Out-Null
Copy-Item "target\$target\release\libcrossui_android.so" (Join-Path $destination "libcrossui_android.so") -Force
