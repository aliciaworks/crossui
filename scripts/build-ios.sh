#!/usr/bin/env bash
set -euo pipefail

rustup target add aarch64-apple-ios
cargo build -p crossui-ffi --target aarch64-apple-ios --release
