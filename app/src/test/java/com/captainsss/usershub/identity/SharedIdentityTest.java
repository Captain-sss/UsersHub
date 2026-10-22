package com.captainsss.usershub.identity;

import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class SharedIdentityTest {
    static JSONObject shared() throws Exception {
        JSONObject w = IdentityFieldsTest.space();
        return w.put("autoInherit", true).put("sharedProfileId", w.getJSONArray("profiles").getJSONObject(0).getString("id"));
    }
    @Test public void futureAppsInheritOneSharedIdentity() throws Exception {
        JSONObject w = shared(); IdentityStore.validateWorkspace(w);
        assertEquals(IdentityStore.assignedProfile(w, "com.example.test"), IdentityStore.assignedProfile(w, "com.example.future"));
        assertFalse(IdentityStore.assignedProfile(w, "com.example.future").isEmpty());
    }
    @Test public void manualAssignmentsRemainOptIn() throws Exception {
        assertEquals("", IdentityStore.assignedProfile(IdentityFieldsTest.space(), "com.example.future"));
    }
    @Test public void managementToolsCannotInherit() throws Exception {
        for (String pkg : new String[]{"com.captainsss.usershub", "com.spoofmydevice", "org.lsposed.manager", "com.topjohnwu.magisk", "com.xj.UsersManager"})
            assertEquals("", IdentityStore.assignedProfile(shared(), pkg));
    }
    @Test public void sharedProfileMustExistAndBeUnique() throws Exception {
        JSONObject w = shared().put("sharedProfileId", "missing");
        assertThrows(Exception.class, () -> IdentityStore.validateWorkspace(w));
        JSONObject two = shared(); two.getJSONArray("profiles").put(IdentityFieldsTest.space().getJSONArray("profiles").getJSONObject(0));
        assertThrows(Exception.class, () -> IdentityStore.validateWorkspace(two));
    }
    @Test public void approvalParserRejectsManagerLauncherAndAmbiguity() {
        String action = "eac4ff56-5472-4214-80fa-8f767d76d84a";
        String scope = "* ReceiverList{x}\napp=1591:system/1000 pid=1591 uid=1000 user=0\nFilter #0:\nAction: \""+action+"\"\nScheme: \"module\"\nrequiredPermission=android.permission.BRICK\n";
        String manager = scope.replace("Filter #0:", "Filter #1:").replace(action, "cd50ccab-ba4c-4974-b206-0c217301c549");
        assertEquals(action, ScopeProtocol.approvalAction(scope + manager));
        assertThrows(IllegalStateException.class, () -> ScopeProtocol.approvalAction(manager));
        assertThrows(IllegalStateException.class, () -> ScopeProtocol.approvalAction(scope + scope.replace(action,"cd50ccab-ba4c-4974-b206-0c217301c549")));
    }
}
