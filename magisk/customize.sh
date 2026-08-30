SKIPMOUNT=false
PROPFILE=true
POSTFSDATA=false
LATESTARTSERVICE=true

ui_print "========================================"
ui_print " UsersHub Core 1.5.1"
ui_print "========================================"
ui_print "Managed Profile creation + diagnostics"
ui_print "Safe per-space deletion"
ui_print "Template third-party app install sync"
ui_print "Accurate real started-state counters"
ui_print "Async gray-icon deep freeze"
ui_print "Multi-user limit override: fw.max_users=99"
ui_print "Managed-profile override: best effort"
ui_print "Real Shared only for genuinely running users"
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
set_perm "$MODPATH/system/bin/usershubcreate" 0 0 0755
set_perm "$MODPATH/system/bin/usershubstatus" 0 0 0755
set_perm "$MODPATH/system/bin/usershubguard" 0 0 0755
set_perm "$MODPATH/system.prop" 0 0 0644
