package com.captainsss.usershub.identity;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Same-user, per-calling-UID read channel. Never exposes the manager's master store. */
public final class IdentityProvider extends ContentProvider {
    public static final Uri URI = Uri.parse("content://com.captainsss.usershub.identity");
    @Override public boolean onCreate() { return true; }

    private String authenticate(String requested) {
        int uid = Binder.getCallingUid();
        if (uid / 100000 != android.os.Process.myUid() / 100000 || uid % 100000 < 10000)
            throw new SecurityException("Cross-user/system callers are not allowed");
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        if (packages != null && IdentityStore.validPackage(requested)) {
            for (String p : packages) if (p.equals(requested)) return p;
        }
        throw new SecurityException("Package does not belong to calling UID");
    }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("prepare".equals(method)) {
            if (Binder.getCallingUid() != 0 && Binder.getCallingUid() != android.os.Process.myUid())
                throw new SecurityException("Root deployment only");
            long token = Binder.clearCallingIdentity();
            try { return prepareVisibility(); }
            catch (Exception e) {
                Bundle b = new Bundle(); b.putString("state", "PREPARE_FAILED");
                b.putString("error", e.getClass().getSimpleName() + ": " + e.getMessage()); return b;
            }
            finally { Binder.restoreCallingIdentity(token); }
        }
        String pkg = authenticate(arg);
        Bundle result = new Bundle();
        try {
            JSONObject w = loadRuntime();
            if (w == null) { result.putString("state", "NO_CONFIG"); return result; }
            String revision = w.getString("revision"); result.putString("revision", revision);
            if (new File(getContext().getFilesDir(), "identity-off").exists() || !w.getBoolean("enabled")) {
                result.putString("state", "DISABLED"); return result;
            }
            android.content.pm.ApplicationInfo info = getContext().getPackageManager().getApplicationInfo(pkg, 0);
            if ((info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 || !IdentityStore.eligiblePackage(pkg)) {
                result.putString("state", "EXCLUDED"); return result;
            }
            JSONObject profile = IdentityStore.findProfile(w, IdentityStore.assignedProfile(w, pkg));
            if (profile == null) { result.putString("state", "UNASSIGNED"); return result; }
            if ("get".equals(method)) {
                JSONObject payload = new JSONObject().put("revision", revision).put("profile", profile)
                        .put("userId", w.getInt("userId")).put("serial", w.getLong("serial"));
                result.putString("config", payload.toString()); result.putString("state", "ASSIGNED");
            } else if ("report".equals(method) && extras != null && revision.equals(extras.getString("revision"))) {
                int pid = Binder.getCallingPid();
                ArrayList<String> source = extras.getStringArrayList("observed"); JSONArray observed = new JSONArray();
                if (source != null) for (String field : source) if (IdentityFields.known(field) && observed.length() < 30) observed.put(field);
                JSONObject receipt = new JSONObject().put("package", pkg).put("pid", pid).put("revision", revision)
                        .put("time", System.currentTimeMillis()).put("observed", observed)
                        .put("verified", safeFields(extras.getStringArrayList("verified")))
                        .put("mismatched", safeFields(extras.getStringArrayList("mismatched")))
                        .put("state", "ENGINE_LOADED");
                File dir = new File(getContext().getFilesDir(), "identity-receipts");
                if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Receipt directory unavailable");
                // Keep separate process receipts; a secondary process must not hide the main process.
                File[] old = dir.listFiles();
                if (old != null) for (File entry : old) if (entry.lastModified() < System.currentTimeMillis() - 7L * 86400000) entry.delete();
                AtomicFile f = new AtomicFile(new File(dir, pkg + "-" + pid + ".json")); FileOutputStream out = null;
                try { out = f.startWrite(); out.write(receipt.toString().getBytes(StandardCharsets.UTF_8)); f.finishWrite(out); }
                catch (Exception e) { if (out != null) f.failWrite(out); throw e; }
                result.putString("state", "RECORDED");
            } else throw new SecurityException("Unsupported operation");
        } catch (SecurityException e) { throw e; }
        catch (Exception e) { result.clear(); result.putString("state", "INVALID_CONFIG"); }
        return result;
    }

    private JSONArray safeFields(ArrayList<String> fields) {
        JSONArray result = new JSONArray();
        if (fields != null) for (String field : fields) if (IdentityFields.known(field) && result.length() < 30) result.put(field);
        return result;
    }

    private Bundle prepareVisibility() throws Exception {
        JSONObject w = loadRuntime(); Set<String> wanted = new HashSet<>();
        if (w != null && w.getBoolean("enabled") && !new File(getContext().getFilesDir(), "identity-off").exists()) {
            Set<String> candidates = new HashSet<>();
            if (w.optBoolean("autoInherit")) {
                for (android.content.pm.ApplicationInfo info : getContext().getPackageManager().getInstalledApplications(0))
                    if ((info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && IdentityStore.eligiblePackage(info.packageName))
                        candidates.add(info.packageName);
            } else {
                Iterator<String> assigned = w.getJSONObject("assignments").keys();
                while (assigned.hasNext()) candidates.add(assigned.next());
            }
            Iterator<String> keys = candidates.iterator();
            while (keys.hasNext()) {
                String pkg = keys.next();
                try {
                    android.content.pm.ApplicationInfo info = getContext().getPackageManager().getApplicationInfo(pkg, 0);
                    if ((info.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) wanted.add(pkg);
                } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {}
            }
        }
        SharedPreferences prefs = getContext().getSharedPreferences("identity-visibility", Context.MODE_PRIVATE);
        Set<String> old = new HashSet<>(prefs.getStringSet("packages", Collections.emptySet()));
        Uri uri = URI.buildUpon().appendPath("config").build();
        // URI grants establish package visibility without modifying the target APK manifest.
        // The URI itself has no file/query read API; call() still authenticates UID + package.
        for (String pkg : wanted) getContext().grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        for (String pkg : old) if (!wanted.contains(pkg)) getContext().revokeUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!prefs.edit().putStringSet("packages", wanted).commit()) throw new IOException("Could not save grants");
        Bundle b = new Bundle(); b.putString("state", "READY"); return b;
    }

    private JSONObject loadRuntime() throws Exception {
        AtomicFile file = new AtomicFile(new File(getContext().getFilesDir(), IdentityStore.RUNTIME_FILE));
        if (!file.getBaseFile().isFile()) return null;
        JSONObject w = new JSONObject(IdentityStore.read(file.openRead())); IdentityStore.validateWorkspace(w);
        UserManager um = (UserManager) getContext().getSystemService(Context.USER_SERVICE);
        long token = Binder.clearCallingIdentity();
        try {
            if (w.getInt("userId") != android.os.Process.myUid() / 100000
                    || w.getLong("serial") != um.getSerialNumberForUser(android.os.Process.myUserHandle()))
                throw new SecurityException("Stale user instance");
        } finally { Binder.restoreCallingIdentity(token); }
        return w;
    }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new SecurityException("Use authenticated call"); }
    @Override public String getType(Uri uri) { return "application/json"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException("Read only"); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException("Read only"); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException("Read only"); }
}
