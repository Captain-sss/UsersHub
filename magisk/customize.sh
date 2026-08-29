SKIPMOUNT=false
PROPFILE=true
POSTFSDATA=false
LATESTARTSERVICE=true

ui_print "========================================"
ui_print " UsersHub Core 1.2.1"
ui_print "========================================"
ui_print "Native APK companion root helper"
ui_print "Deep freeze: third-party + visible system apps"
ui_print "User backend: stop-user / start-user"
ui_print "Shared: auto-init + bind mount for every user"
ui_print "Boot: deep-freeze all secondary users by default"
ui_print "Magisk Action: emergency thaw-all rescue"
ui_print "========================================"

# Disable older modules created during development so two background controllers
# cannot fight over the same users or mount points after reboot.
for OLD in shared_users_download user_spaces_control usershub_shared_control usershub_space_freezer; do
  if [ -d "/data/adb/modules/$OLD" ]; then
    touch "/data/adb/modules/$OLD/disable" 2>/dev/null
    ui_print "Disabled old module: $OLD"
  fi
done

mkdir -p /data/adb/usershub/frozen
chmod 700 /data/adb/usershub /data/adb/usershub/frozen 2>/dev/null

# Default requested behavior: all secondary spaces freeze after boot. The
# emergency Action/rescue-all command removes this flag until explicitly re-enabled.
touch /data/adb/usershub/boot_freeze_enabled
chmod 600 /data/adb/usershub/boot_freeze_enabled 2>/dev/null

set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/system/bin/usershubctl" 0 0 0755
set_perm "$MODPATH/system/bin/usershubshare" 0 0 0755
