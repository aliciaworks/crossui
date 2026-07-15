#!/usr/bin/env bash
set -euo pipefail

target="${CROSSUI_IOS_TARGET:-aarch64-apple-ios}"
rustup target add "$target"
cargo build -p crossui-ffi --target "$target" --release

echo "Built target/$target/release/libcrossui_ffi.a"
