#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/dist"
TMP="$ROOT/build/magisk-package"

rm -rf "$TMP"
mkdir -p "$TMP" "$OUT"
cp -a "$ROOT/magisk/." "$TMP/"
chmod 0755 "$TMP/customize.sh" "$TMP/service.sh" "$TMP/action.sh" "$TMP/uninstall.sh" "$TMP/system/bin/usershubctl" "$TMP/system/bin/usershubshare"
(
  cd "$TMP"
  zip -qr "$OUT/UsersHub-Core-Magisk-v1.3.0.zip" .
)
echo "Built $OUT/UsersHub-Core-Magisk-v1.3.0.zip"
