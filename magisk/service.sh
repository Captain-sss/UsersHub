#!/system/bin/sh

LOG="/data/adb/usershub/usershub.log"

log() {
  echo "$(date '+%F %T') [service] $*" >> "$LOG"
}

mkdir -p /data/adb/usershub/frozen
chmod 700 /data/adb/usershub /data/adb/usershub/frozen 2>/dev/null
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
  log "boot freeze-all begin"
  usershubctl freeze-all >> "$LOG" 2>&1
  log "boot freeze-all end"
else
  log "usershubctl not found"
fi

(
  while true; do
    command -v usershubctl >/dev/null 2>&1 && usershubctl share-all >/dev/null 2>&1
    sleep 15
  done
) &

exit 0
