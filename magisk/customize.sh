SKIPMOUNT=false
PROPFILE=true
POSTFSDATA=false
LATESTARTSERVICE=true

ui_print "========================================"
ui_print " UsersHub Core 1.2.3"
ui_print "========================================"
ui_print "Native APK companion root helper"
ui_print "Deepest freeze: all noncritical installed packages"
ui_print "System + third-party packages are suspended where allowed"
ui_print "User backend: stop-user / start-user"
ui_print "Shared: REAL bind mount only while user is running"
ui_print "No temporary start / no fake Shared initialization"
ui_print "Boot: deep-freeze all secondary users by default"
ui_print "Magisk Action: emergency thaw-all rescue"
ui_print "========================================"

for OLD in shared_users_download user_spaces_control usershub_shared_control usershub_space_freezer; do
  if [ -d "/data/adb/modules/$OLD" ]; then
    touch "/data/adb/modules/$OLD/disable" 2>/dev/null
    ui_print "Disabled old module: $OLD"
  fi
done

mkdir -p /data/adb/usershub/frozen
chmod 700 /data/adb/usershub /data/adb/usershub/frozen 2>/dev/null

touch /data/adb/usershub/boot_freeze_enabled
chmod 600 /data/adb/usershub/boot_freeze_enabled 2>/dev/null

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/system/bin/usershubctl" 0 0 0755
set_perm "$MODPATH/system/bin/usershubshare" 0 0 0755
