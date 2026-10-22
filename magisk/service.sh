#!/system/bin/sh
# Service/action hooks run even when ReSukiSU has no system-mount metamodule.
MODDIR="${0%/*}"
[ -d "$MODDIR/system/bin" ] || MODDIR=/data/adb/modules/usershub_core
if [ -d "$MODDIR/system/bin" ]; then
  PATH="$MODDIR/system/bin:$PATH"
  export PATH
fi


BASE="/data/adb/usershub"
STATE="$BASE/frozen"
LOG="$BASE/usershub.log"
BOOT_FLAG="$BASE/boot_freeze_enabled"

log() { echo "$(date '+%F %T') [service] $*" >> "$LOG"; }

mkdir -p "$STATE"
chmod 700 "$BASE" "$STATE" 2>/dev/null
touch "$LOG"
chmod 600 "$LOG" 2>/dev/null

rm -rf "$STATE"/*.scanlock 2>/dev/null
rm -f "$STATE"/*.scanning 2>/dev/null

N=0
while [ "$(getprop sys.boot_completed 2>/dev/null)" != "1" ] && [ "$N" -lt 150 ]; do
  sleep 2
  N=$((N + 1))
done
sleep 8

# Repair full users created by older UsersHub versions that existed in
# UserManager but had never completed the Android/MIUI user setup pass.
if command -v usershubcreate >/dev/null 2>&1; then
  usershubcreate repair-incomplete >> "$LOG" 2>&1 || true
fi

if command -v usershubctl >/dev/null 2>&1; then
  if [ -f "$BOOT_FLAG" ]; then
    log "boot immediate freeze-all begin"
    usershubctl freeze-all >> "$LOG" 2>&1
    log "boot immediate freeze-all queued"
  else
    log "boot auto-freeze disabled"
  fi
else
  log "usershubctl not found"
fi

# Dedicated deep-freeze queue worker.
(
  while true; do
    if command -v usershubctl >/dev/null 2>&1; then
      usershubctl process-pending >/dev/null 2>&1
    fi
    sleep 1
  done
) &

# Lifecycle guard is intentionally independent from deep package scans.
# It keeps frozen users really stopped and prevents Shared from being
# re-mounted while Android is still finishing an asynchronous stop.
(
  TICK=0
  while true; do
    if command -v usershubguard >/dev/null 2>&1; then
      usershubguard all >/dev/null 2>&1
    fi

    TICK=$((TICK + 1))
    if [ "$TICK" -ge 12 ]; then
      if command -v usershubctl >/dev/null 2>&1; then
        usershubctl maintain-frozen >/dev/null 2>&1
      fi
      TICK=0
    fi

    sleep 3
  done
) &

exit 0
