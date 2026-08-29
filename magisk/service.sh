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
  usershubctl share-all >> "$LOG" 2>&1
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

# Keep Shared attached for newly-created users and keep apps inside already
# frozen spaces suspended. Deep freeze includes third-party packages plus
# launcher-visible system apps. We deliberately do NOT repeatedly stop users,
# so entering a frozen space to inspect gray icons does not immediately kick
# the user back to user 0.
(
  while true; do
    if command -v usershubctl >/dev/null 2>&1; then
      usershubctl share-all >/dev/null 2>&1
      usershubctl maintain-frozen >/dev/null 2>&1
    fi
    sleep 30
  done
) &

exit 0
