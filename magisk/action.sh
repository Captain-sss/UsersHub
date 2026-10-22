#!/system/bin/sh

# ReSukiSU/KernelSU can keep modules installed without projecting system/bin.
# Keep helpers available to background workers using the actual module path.
MODDIR="${0%/*}"
[ -d "$MODDIR/system/bin" ] || MODDIR="/data/adb/modules/usershub_core"
if [ -d "$MODDIR/system/bin" ]; then
  PATH="$MODDIR/system/bin:$PATH"
  export PATH
fi

ui_print() { echo "$*"; }

ui_print "========================================"
ui_print " UsersHub Emergency Rescue"
ui_print "========================================"
ui_print "Thawing all UsersHub-managed spaces..."

if command -v usershubctl >/dev/null 2>&1; then
  usershubctl rescue-all
  RC=$?
else
  ui_print "usershubctl not found"
  RC=1
fi

if [ "$RC" -eq 0 ]; then
  ui_print "Rescue completed."
  ui_print "Boot auto-freeze is now OFF."
  ui_print "Re-enable later with: usershubctl boot-freeze on"
else
  ui_print "Rescue finished with errors; check /data/adb/usershub/usershub.log"
fi

exit "$RC"
