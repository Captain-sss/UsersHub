#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/dist"
TMP="$ROOT/build/magisk-package"

rm -rf "$TMP"
mkdir -p "$TMP" "$OUT"
cp -a "$ROOT/magisk/." "$TMP/"

# A local Windows checkout may contain CRLF line endings. Android executes the
# files under system/bin directly, so a CRLF shebang becomes /system/bin/sh\r
# and fails with "No such file or directory". Normalize package text files
# without changing the checked-out sources.
find "$TMP" -type f \( \
  -name '*.sh' -o \
  -name 'module.prop' -o \
  -name 'system.prop' -o \
  -path "$TMP/system/bin/*" \
\) -exec sed -i 's/\r$//' {} +

chmod 0755 \
  "$TMP/customize.sh" \
  "$TMP/service.sh" \
  "$TMP/action.sh" \
  "$TMP/uninstall.sh" \
  "$TMP/system/bin/usershubctl" \
  "$TMP/system/bin/usershubshare" \
  "$TMP/system/bin/usershubcreate" \
  "$TMP/system/bin/usershubapps" \
  "$TMP/system/bin/usershubstatus" \
  "$TMP/system/bin/usershubguard" \
  "$TMP/system/bin/usershubidentity"
rm -f "$OUT/UsersHub-Core-Magisk-v1.9.1.zip"
if command -v zip >/dev/null 2>&1; then
  (
    cd "$TMP"
    zip -qr "$OUT/UsersHub-Core-Magisk-v1.9.1.zip" .
  )
elif command -v jar >/dev/null 2>&1; then
  jar -J-XX:-UsePerfData --create --file "$OUT/UsersHub-Core-Magisk-v1.9.1.zip" --no-manifest -C "$TMP" .
else
  echo "zip or JDK jar is required" >&2
  exit 1
fi
echo "Built $OUT/UsersHub-Core-Magisk-v1.9.1.zip"
