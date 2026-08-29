#!/system/bin/sh

BASE="/data/adb/usershub"
LOG="$BASE/usershub.log"
BOOT_FLAG="$BASE/boot_freeze_enabled"

log() {
  echo "$(date '+%F %T') [service] $*" >> "$LOG"
}

mkdir -p "$BASE/frozen"
chmod 700 "$BASE" "$BASE/frozen" 2>/dev/null
touch "$LOG"
chmod 600 "$LOG" 2>/dev/null

N=0
while [ "$(getprop sys.boot_completed 2>/dev/null)" != "1" ] && [ "$N" -lt 150 ]; do
  sleep 2
  N=$((N + 1))
done
sleep 10

if command -v usershubctl >/dev/null 2>&1; then
  if [ -f "$BOOT_FLAG" ]; then
    log "boot deep-freeze-all begin"
    usershubctl freeze-all >> "$LOG" 2>&1
    log "boot deep-freeze-all end"
  else
    log "boot auto-freeze disabled by rescue flag/state"
  fi
else
  log "usershubctl not found"
fi

# Shared follows the real Android user lifecycle:
# running user -> bind-mount the real user-0 Shared folder
# stopped/frozen user -> unmount Shared
# Never start a stopped user just to initialize or manufacture a mountpoint.
if command -v usershubshare >/dev/null 2>&1; then
  usershubshare all >> "$LOG" 2>&1
fi

(
  while true; do
    if command -v usershubshare >/dev/null 2>&1; then
      usershubshare all >/dev/null 2>&1
    fi
    if command -v usershubctl >/dev/null 2>&1; then
      usershubctl maintain-frozen >/dev/null 2>&1
    fi
    sleep 15
  done
) &

exit 0
