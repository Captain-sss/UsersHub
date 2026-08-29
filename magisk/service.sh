#!/system/bin/sh

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

# Dedicated deep-freeze queue worker. It may spend time processing hundreds of
# packages, so it is intentionally separate from the Shared lifecycle loop.
(
  while true; do
    if command -v usershubctl >/dev/null 2>&1; then
      usershubctl process-pending >/dev/null 2>&1
    fi
    sleep 1
  done
) &

# Shared and light maintenance remain responsive even while deep scans run.
(
  TICK=0
  while true; do
    if command -v usershubshare >/dev/null 2>&1; then
      usershubshare all >/dev/null 2>&1
    fi

    TICK=$((TICK + 1))
    if [ "$TICK" -ge 12 ]; then
      if command -v usershubctl >/dev/null 2>&1; then
        usershubctl maintain-frozen >/dev/null 2>&1
      fi
      TICK=0
    fi

    sleep 5
  done
) &

exit 0
