package com.captainsss.usershub.identity;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class IdentityFieldsTest {
    @Test public void generatedIdentifiersAreValidAndDistinctWithinBatch() {
        for (String key : IdentityFields.IDS) {
            Set<String> values = new HashSet<>();
            for (int i = 0; i < 100; i++) { String v = IdentityFields.generate(key); IdentityFields.validate(key, v); assertTrue(key, values.add(v)); }
        }
    }
    @Test public void luhnKnownVector() { assertEquals("79927398713", IdentityFields.luhn("7992739871")); }
    @Test public void buildCompanionFieldsAreKnownAndStrict() {
        IdentityFields.validate("build_id", "AP3A.240905.015.A2");
        IdentityFields.validate("build_incremental", "13003188");
        IdentityFields.validate("build_type", "user");
        IdentityFields.validate("build_tags", "release-keys");
        assertEquals("ID", IdentityFields.buildField("build_id"));
        assertEquals("TYPE", IdentityFields.buildField("build_type"));
        assertTrue(IdentityFields.isBuildVersionField("build_incremental"));
        assertThrows(IllegalArgumentException.class, () -> IdentityFields.validate("build_id", "bad/id"));
    }
    @Test public void rejectsUnknownAndMalformedFields() {
        assertThrows(IllegalArgumentException.class, () -> IdentityFields.validate("sdk", "35"));
        assertThrows(IllegalArgumentException.class, () -> IdentityFields.validate("android_id", "123"));
        assertThrows(IllegalArgumentException.class, () -> IdentityFields.validate("model", "a\nb"));
        String valid = IdentityFields.generate("imei"); String bad = valid.substring(0,14) + ((valid.charAt(14)-'0'+1)%10);
        assertThrows(IllegalArgumentException.class, () -> IdentityFields.validate("imei", bad));
    }
    static JSONObject space() throws Exception {
        JSONObject values = new JSONObject(IdentityFields.generateAll()); JSONArray fields = new JSONArray(IdentityFields.IDS);
        String id = UUID.randomUUID().toString();
        JSONObject profile = new JSONObject().put("id", id).put("name", "1").put("values", values).put("enabledFields", fields);
        return new JSONObject().put("version",1).put("userId",22).put("serial",75).put("enabled",false)
                .put("revision",UUID.randomUUID().toString()).put("profiles",new JSONArray().put(profile))
                .put("assignments",new JSONObject().put("com.example.test",id));
    }
    @Test public void acceptsExplicitValidBindings() throws Exception { IdentityStore.validateWorkspace(space()); }
    @Test public void rejectsOwnerAndDanglingBinding() throws Exception {
        JSONObject owner = space().put("userId",0);
        assertThrows(Exception.class, () -> IdentityStore.validateWorkspace(owner));
        JSONObject w = space(); w.getJSONObject("assignments").put("com.example.test",UUID.randomUUID().toString());
        assertThrows(Exception.class, () -> IdentityStore.validateWorkspace(w));
    }
    @Test public void rejectsWrongInstanceAndUnsafePackage() throws Exception {
        JSONObject w = space(); JSONObject backup = IdentityStore.empty();
        backup.getJSONObject("workspaces").put("22:74",w);
        assertThrows(Exception.class, () -> IdentityStore.parse(backup.toString()));
        assertFalse(IdentityStore.validPackage("com.test;reboot")); assertFalse(IdentityStore.validPackage("../com.test"));
    }
    @Test public void rejectsUnversionedBackupAndDuplicateFields() throws Exception {
        assertThrows(Exception.class, () -> IdentityStore.parse("{\"version\":9,\"workspaces\":{}}"));
        JSONObject w = space(); w.getJSONArray("profiles").getJSONObject(0).getJSONArray("enabledFields").put("android_id");
        assertThrows(Exception.class, () -> IdentityStore.validateWorkspace(w));
    }
}
