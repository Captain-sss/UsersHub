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

if command -v usershubshare >/dev/null 2>&1; then
  # v1.2.1: initialize media storage for never-started secondary users when
  # necessary, mount Shared, and restore their original stopped state.
  log "initial Shared initialization begin"
  usershubshare all >> "$LOG" 2>&1
  log "initial Shared initialization end"
else
  log "usershubshare not found"
fi

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

# Keep Shared attached for existing and newly-created users. usershubshare only
# performs a temporary start when a user's media directory does not yet exist,
# and restores that user to stopped state afterward. It does not keep users
# running. Frozen-space package reconciliation remains separate.
(
  while true; do
    if command -v usershubshare >/dev/null 2>&1; then
      usershubshare all >/dev/null 2>&1
    fi
    if command -v usershubctl >/dev/null 2>&1; then
      usershubctl maintain-frozen >/dev/null 2>&1
    fi
    sleep 30
  done
) &

exit 0
