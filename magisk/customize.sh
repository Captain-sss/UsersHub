SKIPMOUNT=false
PROPFILE=true
POSTFSDATA=false
LATESTARTSERVICE=true

ui_print "========================================"
ui_print " UsersHub Core 1.0.0"
ui_print "========================================"
ui_print "Native APK companion root helper"
ui_print "Freeze: suspend third-party apps + stop user"
ui_print "Thaw: restore UsersHub-owned suspensions"
ui_print "Shared: /data/media/0/Download/Shared"
ui_print "Boot: freeze all secondary users by default"
ui_print "========================================"

mkdir -p /data/adb/usershub/frozen
chmod 700 /data/adb/usershub /data/adb/usershub/frozen 2>/dev/null

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/system/bin/usershubctl" 0 0 0755
