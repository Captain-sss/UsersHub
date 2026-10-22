# UsersHub 1.9.1 · ReSukiSU/KernelSU compatibility test plan

Device under test: Redmi K30 Pro (`lmi`), MIUI 14.0.5.0 / Android 12, ReSukiSU + SuSFS.
This is a candidate for real-device validation, **not** a verified release.

## Rationale
KernelSU executes `service.sh`, `action.sh` and `system.prop` even without a metamodule, but does not project `system/bin` in that case. APK/Core scripts now prepend the **enabled module's real** `/data/adb/modules/usershub_core/system/bin` path and keep Magisk behavior intact.

ReSukiSU/KernelSU BusyBox is usually at `/data/adb/ksu/bin/busybox` rather than `/data/adb/magisk/busybox`; identity/LSPosed operations now select the available binary. Shared mounts attempt the init mount namespace via BusyBox nsenter on KernelSU, with Magisk `su -M` as fallback.

## Safety
- Keep current working Boot and DTBO backups. No kernel/partition flashing is required for this update.
- Before updating, back up `/data/adb/usershub` to a private computer location. It contains freeze state, workspace names, and possibly identity configuration; **do not publish** it.
- **Do not uninstall UsersHub Core first**. Its uninstall script invokes rescue and removes `/data/adb/usershub`. Update the same module ID `usershub_core` in-place.
- No "clear data", factory reset, or removal of any Managed Profile.
- APK and Core version should both be 1.9.1. The APK must have the same signing identity as v1.9.0 for `adb install -r`; if Android rejects signature replacement, stop.

## Pre-install checks (read-only)

```bat
adb shell su -c "id"
adb shell su -c "cat /data/adb/modules/usershub_core/module.prop"
adb shell su -c "ls -l /data/adb/ksu/bin/busybox"
adb shell su -c "ls -l /data/adb/modules/usershub_core/system/bin"
adb shell su -c "PATH=/data/adb/modules/usershub_core/system/bin:$PATH; export PATH; usershubstatus --plain"
```

If `/data/adb/ksu/bin/busybox` is missing, gather Root manager diagnostics before attempting identity/LSPosed tests.

### Back up state (contains private per-space data)

```bat
adb shell su -c "tar -czf /sdcard/Download/usershub-state-before-191.tgz -C /data/adb usershub"
adb pull /sdcard/Download/usershub-state-before-191.tgz .
```

Keep the archive local. Check it exists and has non-zero size; do not share the contents.

## Upgrade and smoke test

1. In the ReSukiSU module manager, install `UsersHub-Core-Magisk-v1.9.1.zip` as an **update** of the existing `usershub_core` module. Do not uninstall or clear state.
2. Install `UsersHub-v1.9.1.apk` as an upgrade. Same signing certificate should be required.
3. Reboot, grant UsersHub Root, and verify the app no longer reports Core missing.
4. Read-only check: `adb shell su -c "PATH=/data/adb/modules/usershub_core/system/bin:$PATH; usershubstatus --plain"`.
5. Test creating an explicitly disposable workspace; verify actual user ID and no inherited third-party app installs. Then test start, freeze, thaw, delete **only that new test workspace**.
6. For an already running workspace, test Shared mount and file visibility (no file deletes); verify `/proc/1/mountinfo` or `usershubshare status` without relying only on the app badge.
7. Verify LSPosed version, UsersHub scope-check and profile deployment on a disposable third-party app; do not change production profiles while diagnosing.
8. Reboot again to check boot auto-freeze, scope synchronization and the background service.

If steps 1–4 fail, **do not uninstall the Core**, and do not delete workspace IDs or data. Export the relevant logs with private values removed and restore the previous matching Core/APK version if needed.

## Not yet validated
Real-device namespace mounting, SELinux policy, MIUI max managed profile limit and LSPosed 1.9.2/7024 scope receipts. CI shell syntax and APK compilation alone do not verify those behaviors.
