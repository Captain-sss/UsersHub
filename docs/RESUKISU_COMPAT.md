# ReSukiSU / KernelSU compatibility notes (prototype from v1.5.2)

## Scope and caveats

This branch is **based on the repository's v1.5.2 sources**. Do **not** downgrade a deployed UsersHub v1.9.0 app/Core to these artifacts. Port the same compatibility logic to the actual v1.9.0 sources before distribution.

For Android 12 / Redmi K30 Pro (lmi) running ReSukiSU, the Core module's scripts may already exist under `/data/adb/modules/usershub_core/system/bin/` while a missing metamodule prevents projection to `/system/bin`. An APK checking only `command -v usershubstatus` then reports Core missing despite an installed, enabled module.

The fix in this branch:
- Prefixes `PATH` inside all APK root operations when an enabled `usershub_core` module is installed, retaining `su -c` authorization.
- Prefixes `PATH` in background `service.sh`, module `action.sh`, and cleanup `uninstall.sh` so their internal calls also work without the system mount.
- Avoids copying scripts to `/system`, disabling SELinux or requiring a metamodule just to invoke shell helpers.
- Preserves all existing space IDs, freeze state, Shared data, and command names. It does not change the kernel or Root manager.

**This only addresses executable lookup.** If the real UsersHub v1.9.0 Core relies on systemless mount overlays, mount-master namespaces, Magisk-specific `resetprop` or `su -M` semantics, additional checks and changes will be required. Do not assume all functionality is validated just because the APK status screen loads.

## Read-only diagnosis on the user's v1.9.0 phone

```sh
adb shell su -c 'id'
adb shell su -c 'ls -la /data/adb/modules/usershub_core'
adb shell su -c 'ls -la /data/adb/modules/usershub_core/system/bin'
adb shell su -c 'command -v usershubstatus || echo NOT_IN_PATH'
adb shell su -c 'PATH=/data/adb/modules/usershub_core/system/bin:$PATH; export PATH; command -v usershubstatus; usershubstatus --plain'
```

If `usershubstatus` is not part of v1.9.0, inspect the actual APK and Core entry points first. If the last line returns expected status but `command -v` previously failed, the non-mounted module PATH is the most likely trigger. No flash, wipe, Root switch or metamodule is required to run these diagnostics.

## Validation when v1.9.0 sources are available

1. Verify Core module filename/version and expected entry points.
2. Install the matching APK and Core update **without wiping `/data/adb/usershub`**, and reboot.
3. Verify `su` authorization and status view, create a disposable test workspace, then thaw/freeze and verify app icons.
4. Verify `service.sh` auto-freeze and Shared mount/unmount from the appropriate mount namespace.
5. Test on Magisk and ReSukiSU with/without metamodule; do not remove users, install system props or change production spaces just to test compatibility.

### Status

Source-code compatibility patch prepared against v1.5.2. Not a tested v1.9.0 release; requires the current v1.9.0 codebase and real-device regression testing.
