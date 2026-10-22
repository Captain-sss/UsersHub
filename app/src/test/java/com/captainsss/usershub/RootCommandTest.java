package com.captainsss.usershub;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public final class RootCommandTest {
    @Test public void modulePathIsPrependedBeforeCoreOperations() {
        String command = RootCommand.withCorePath("usershubstatus --plain");
        assertTrue(command.contains("/data/adb/modules/usershub_core"));
        assertTrue(command.contains("PATH=\"$uhcore/system/bin:$PATH\""));
        assertTrue(command.endsWith("usershubstatus --plain"));
    }

    @Test public void disabledOrPendingRemovalCoreIsNotUsed() {
        String command = RootCommand.withCorePath("usershubidentity list");
        assertTrue(command.contains("! -e \"$uhcore/disable\""));
        assertTrue(command.contains("! -e \"$uhcore/remove\""));
        assertFalse(command.contains("/data/adb/magisk/busybox"));
    }
}
