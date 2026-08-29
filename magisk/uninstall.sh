#!/system/bin/sh

if [ -x /system/bin/usershubctl ]; then
  /system/bin/usershubctl thaw-all >/dev/null 2>&1
fi

OUT="$(cmd user list 2>/dev/null)"
[ -z "$OUT" ] && OUT="$(pm list users 2>/dev/null)"

for U in $(echo "$OUT" | sed -n 's/.*UserInfo{\([0-9][0-9]*\):.*/\1/p'); do
  [ "$U" -ge 10 ] 2>/dev/null || continue
  [ "$U" -lt 900 ] 2>/dev/null || continue
  DST="/data/media/$U/Download/Shared"
  if command -v su >/dev/null 2>&1; then
    su -M -c "umount '$DST' 2>/dev/null" >/dev/null 2>&1
  else
    umount "$DST" >/dev/null 2>&1
  fi
done

rm -rf /data/adb/usershub/frozen 2>/dev/null
exit 0
