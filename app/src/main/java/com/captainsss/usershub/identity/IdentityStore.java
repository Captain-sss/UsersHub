package com.captainsss.usershub.identity;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Main-user source of truth. Runtime copies contain only one real user instance. */
public final class IdentityStore {
    public static final int VERSION = 1;
    public static final int MAX_BYTES = 524288;
    public static final String RUNTIME_FILE = "identity-runtime.json";
    private final AtomicFile file;
    private JSONObject root;

    public IdentityStore(Context context) throws Exception {
        file = new AtomicFile(new File(context.getFilesDir(), "identity-store.json"));
        root = file.getBaseFile().exists() ? parse(read(file.openRead())) : empty();
    }

    public static JSONObject empty() throws Exception {
        return new JSONObject().put("version", VERSION).put("workspaces", new JSONObject());
    }

    public static String read(InputStream stream) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > MAX_BYTES) throw new IOException("配置超过大小限制");
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    public static JSONObject parse(String value) throws Exception {
        if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IOException("配置过大");
        JSONObject obj = new JSONObject(value);
        if (obj.getInt("version") != VERSION) throw new IOException("不支持的配置版本");
        JSONObject spaces = obj.getJSONObject("workspaces");
        if (spaces.length() > 200) throw new IOException("空间数量超出限制");
        Iterator<String> keys = spaces.keys();
        while (keys.hasNext()) {
            String key = keys.next(); JSONObject w = spaces.getJSONObject(key);
            validateWorkspace(w);
            if (!key.equals(key(w.getInt("userId"), w.getLong("serial")))) throw new IOException("空间标识不匹配");
        }
        return obj;
    }

    public static String key(int user, long serial) { return user + ":" + serial; }

    public synchronized JSONObject workspace(int user, long serial) throws Exception {
        JSONObject existing = root.getJSONObject("workspaces").optJSONObject(key(user, serial));
        if (existing != null) return new JSONObject(existing.toString());
        return new JSONObject().put("version", VERSION).put("userId", user).put("serial", serial)
                .put("enabled", false).put("revision", UUID.randomUUID().toString())
                .put("profiles", new JSONArray()).put("assignments", new JSONObject());
    }

    public synchronized void save(JSONObject workspace) throws Exception {
        validateWorkspace(workspace);
        JSONObject next = new JSONObject(root.toString());
        next.getJSONObject("workspaces").put(key(workspace.getInt("userId"), workspace.getLong("serial")),
                new JSONObject(workspace.toString()));
        persist(next); root = next;
    }

    public synchronized String exportBackup() throws Exception { return root.toString(2); }

    public synchronized void importBackup(String text) throws Exception {
        JSONObject incoming = parse(text);
        JSONObject next = new JSONObject(root.toString());
        JSONObject spaces = incoming.getJSONObject("workspaces");
        Iterator<String> keys = spaces.keys();
        while (keys.hasNext()) {
            String key = keys.next(); JSONObject w = spaces.getJSONObject(key);
            // Restoring a backup must never silently activate hooks.
            w.put("enabled", false).put("revision", UUID.randomUUID().toString());
            next.getJSONObject("workspaces").put(key, w);
        }
        persist(next); root = next;
    }

    public synchronized JSONObject[] allWorkspaces() throws Exception {
        JSONObject spaces = root.getJSONObject("workspaces"); List<JSONObject> out = new ArrayList<>();
        Iterator<String> keys = spaces.keys();
        while (keys.hasNext()) out.add(new JSONObject(spaces.getJSONObject(keys.next()).toString()));
        return out.toArray(new JSONObject[0]);
    }

    public synchronized JSONObject newProfile(String name) throws Exception {
        Set<String> used = new HashSet<>();
        for (JSONObject w : allWorkspaces()) {
            JSONArray ps = w.getJSONArray("profiles");
            for (int i = 0; i < ps.length(); i++) {
                JSONObject values = ps.getJSONObject(i).getJSONObject("values");
                for (String field : IdentityFields.IDS) used.add(field + ":" + values.optString(field));
            }
        }
        JSONObject values = new JSONObject(); JSONArray enabled = new JSONArray();
        for (String field : IdentityFields.IDS) {
            String value; do { value = IdentityFields.generate(field); } while (used.contains(field + ":" + value));
            values.put(field, value); enabled.put(field);
        }
        return new JSONObject().put("id", UUID.randomUUID().toString()).put("name", name)
                .put("values", values).put("enabledFields", enabled);
    }

    public static JSONObject findProfile(JSONObject workspace, String id) throws Exception {
        JSONArray profiles = workspace.getJSONArray("profiles");
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.getJSONObject(i);
            if (p.getString("id").equals(id)) return p;
        }
        return null;
    }

    public static boolean validPackage(String value) {
        return value != null && value.length() <= 220 && value.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+");
    }

    /** Management tools must not hook themselves, even in automatic third-party mode. */
    public static boolean eligiblePackage(String pkg) {
        return validPackage(pkg) && !pkg.equals("com.captainsss.usershub")
                && !pkg.equals("com.spoofmydevice") && !pkg.equals("com.xj.UsersManager")
                && !pkg.equals("org.lsposed.manager") && !pkg.toLowerCase(Locale.ROOT).contains("magisk");
    }

    public static String assignedProfile(JSONObject w, String pkg) {
        if (!eligiblePackage(pkg)) return "";
        return w.optBoolean("autoInherit") ? w.optString("sharedProfileId")
                : w.optJSONObject("assignments").optString(pkg);
    }

    /** Replaces rather than appends: there is exactly one current shared identity. */
    public synchronized JSONObject replaceShared(int user, long serial, Collection<String> packages) throws Exception {
        JSONObject next = workspace(user, serial);
        JSONObject profile = newProfile("空间设备配置"); String id = profile.getString("id");
        JSONObject assignments = new JSONObject();
        for (String pkg : packages) if (eligiblePackage(pkg)) assignments.put(pkg, id);
        next.put("profiles", new JSONArray().put(profile)).put("assignments", assignments)
                .put("autoInherit", true).put("sharedProfileId", id).put("enabled", true)
                .put("revision", UUID.randomUUID().toString());
        save(next); return next;
    }

    public static void validateWorkspace(JSONObject w) throws Exception {
        if (w.getInt("version") != VERSION || w.getInt("userId") < 10 || w.getInt("userId") >= 900 || w.getLong("serial") < 0)
            throw new IOException("不允许的空间身份");
        UUID.fromString(w.getString("revision")); w.getBoolean("enabled");
        JSONArray profiles = w.getJSONArray("profiles"); JSONObject assignments = w.getJSONObject("assignments");
        if (profiles.length() > 50 || assignments.length() > 500) throw new IOException("配置数量超限");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.getJSONObject(i); String id = p.getString("id"); UUID.fromString(id);
            if (!ids.add(id)) throw new IOException("配置 ID 重复");
            String name = p.getString("name").trim();
            if (name.isEmpty() || name.length() > 40) throw new IOException("配置名称应为 1–40 个字符");
            JSONObject values = p.getJSONObject("values"); JSONArray fields = p.getJSONArray("enabledFields");
            if (values.length() > 30 || fields.length() > 30) throw new IOException("字段数量超限");
            Iterator<String> keys = values.keys();
            while (keys.hasNext()) { String key = keys.next(); IdentityFields.validate(key, values.getString(key)); }
            Set<String> uniqueFields = new HashSet<>();
            for (int j = 0; j < fields.length(); j++) {
                String field = fields.getString(j);
                if (!uniqueFields.add(field) || !values.has(field)) throw new IOException("启用字段缺少值或重复");
                IdentityFields.validate(field, values.getString(field));
            }
        }
        Iterator<String> packages = assignments.keys();
        while (packages.hasNext()) {
            String pkg = packages.next();
            if (!validPackage(pkg) || pkg.equals("com.captainsss.usershub") || !ids.contains(assignments.getString(pkg)))
                throw new IOException("应用绑定无效：" + pkg);
        }
        if (w.optBoolean("autoInherit")) {
            String shared = w.getString("sharedProfileId");
            if (profiles.length() != 1 || !ids.contains(shared)) throw new IOException("自动继承必须只有一套空间配置");
            Iterator<String> bound = assignments.keys();
            while (bound.hasNext()) {
                String pkg = bound.next();
                if (!eligiblePackage(pkg) || !shared.equals(assignments.getString(pkg)))
                    throw new IOException("自动继承的应用必须共用空间配置");
            }
        }
    }

    private void persist(JSONObject next) throws Exception {
        String text = next.toString(2); parse(text);
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(text.getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch (Exception error) { if (out != null) file.failWrite(out); throw error; }
    }
}
