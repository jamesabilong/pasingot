#!/usr/bin/env bash
set -euo pipefail

# Recovery profile verified on the 8 GB Apple Silicon development Mac.
# Keep Docker stopped separately when memory is constrained; never stop it here.
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
emulator_bin="$sdk_root/emulator/emulator"
if [[ ! -x "$emulator_bin" ]]; then
  echo "Android Emulator not found: $emulator_bin" >&2
  echo "Set ANDROID_SDK_ROOT to your installed Android SDK." >&2
  exit 1
fi

# Cold boot avoids stale graphics snapshots and preserves existing app data.
# Hardware GLES avoids software rendering; disabling Vulkan avoids MoltenVK.
exec "$emulator_bin" -avd "${1:-Pixel_8}" -no-snapshot -no-boot-anim \
  -gpu host -feature -Vulkan -camera-back none -camera-front none
