#!/system/bin/sh

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
