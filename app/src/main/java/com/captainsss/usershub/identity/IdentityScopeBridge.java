package com.captainsss.usershub.identity;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Root-only adapter for the installed LSPosed 1.9.2 / service API 100.
 * Reads its database, never writes it. Additions go through its own approval receiver,
 * which commits the scope and refreshes the daemon cache. No apps/users are started.
 */
public final class IdentityScopeBridge {
    private static final String PKG = "com.captainsss.usershub";
    private static final String CALLBACK = "io.github.libxposed.service.IXposedScopeCallback";
    private static final File BASE = new File("/data/adb/usershub/identity");
    private static Context context;
    private static String action;

    public static void main(String[] args) {
        if (android.os.Process.myUid() != 0) throw new SecurityException("root required");
        int rc = 0;
        try {
            checkVersion();
            if (args.length == 1 && args[0].equals("check")) {
                inspect(); System.out.println("接口检查通过；未更改任何作用域");
            } else if (args.length == 2) {
                sync(Integer.parseInt(args[0]), Long.parseLong(args[1]));
            } else throw new IllegalArgumentException("Expected check or USER SERIAL");
        } catch (Exception e) { System.out.println("作用域待处理：" + e.getMessage()); rc = 1; }
        System.exit(rc);
    }

    private static void checkVersion() throws Exception {
        String prop = IdentityStore.read(new FileInputStream("/data/adb/modules/zygisk_lsposed/module.prop"));
        if (!prop.matches("(?s).*\\bversionCode=7024(?:\\r?\\n|$).*"))
            throw new IOException("当前自动作用域适配仅验证 LSPosed 1.9.2 (7024)，其他版本请手动设置");
    }

    private static SQLiteDatabase database() {
        SQLiteDatabase db = SQLiteDatabase.openDatabase("/data/adb/lspd/config/modules_config.db", null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        if (db.getVersion() != 3) { db.close(); throw new IllegalStateException("不支持的 LSPosed 数据结构"); }
        return db;
    }

    private static boolean enabled(SQLiteDatabase db, String pkg) {
        try (Cursor c = db.rawQuery("SELECT enabled FROM modules WHERE module_pkg_name=?", new String[]{pkg})) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }
    private static Set<String> scopes(SQLiteDatabase db, String pkg, int user) {
        Set<String> set = new TreeSet<>();
        try (Cursor c = db.rawQuery("SELECT s.app_pkg_name FROM scope s JOIN modules m ON m.mid=s.mid WHERE m.module_pkg_name=? AND s.user_id=?",
                new String[]{pkg, Integer.toString(user)})) { while (c.moveToNext()) set.add(c.getString(0)); }
        return set;
    }

    private static void inspect() throws Exception {
        try (SQLiteDatabase db = database()) {
            if (!enabled(db, PKG)) throw new IOException("请先在 LSPosed 中启用 UsersHub");
            if (enabled(db, "com.spoofmydevice")) throw new IOException("请先关闭 SpoofMyDevice，避免叠加旧 Hook");
        }
        action = ScopeProtocol.approvalAction(exec("dumpsys", "activity", "broadcasts"));
    }

    private static void sync(int user, long serial) throws Exception {
        File report = new File(BASE, user + "-" + serial + ".scope.json");
        JSONObject state = new JSONObject().put("time", System.currentTimeMillis());
        try {
            String instances = exec("usershubidentity", "list");
            if (!Arrays.asList(instances.split("\\R")).contains(user + "|" + serial)) throw new IOException("空间已删除或身份已变更");
            JSONObject w = new JSONObject(IdentityStore.read(new FileInputStream(new File(BASE, user + "-" + serial + ".json"))));
            IdentityStore.validateWorkspace(w);
            if (w.getInt("userId") != user || w.getLong("serial") != serial) throw new IOException("空间身份不匹配");
            state.put("revision", w.getString("revision"));
            if (!w.getBoolean("enabled") || new File(BASE, user + "-" + serial + ".force_off").exists()) {
                state.put("state", "DISABLED"); write(report, state); return;
            }
            List<String> packages = new ArrayList<>();
            for (String line : exec("pm", "list", "packages", "-3", "--user", Integer.toString(user)).split("\\R")) {
                if (!line.startsWith("package:")) continue;
                String pkg = line.substring(8).trim();
                if (IdentityStore.eligiblePackage(pkg) && !IdentityStore.assignedProfile(w, pkg).isEmpty()) packages.add(pkg);
            }
            Collections.sort(packages);
            state.put("packages", new JSONArray(packages));
            inspect();
            // LSPosed drops scope entries when the module itself isn't installed in
            // that user. install-existing only adds our package relationship; unlike
            // the manager's install API it does NOT start the user or create CE dirs here.
            String modulePackages = exec("pm", "list", "packages", "--user", Integer.toString(user), PKG);
            if (!Arrays.asList(modulePackages.split("\\R")).contains("package:" + PKG)) {
                exec("cmd", "package", "install-existing", "--user", Integer.toString(user), PKG);
                exec("pm", "enable", "--user", Integer.toString(user), PKG);
            }
            exec("pm", "disable", "--user", Integer.toString(user), PKG + "/.MainActivity");
            Set<String> existing;
            try (SQLiteDatabase db = database()) { existing = scopes(db, PKG, user); }
            int added = 0;
            for (String pkg : packages) if (!existing.contains(pkg)) { approve(user, pkg); added++; }
            if (added > 0) SystemClock.sleep(1200); // Allow LSPosed's asynchronous cache validation to finish.
            try (SQLiteDatabase db = database()) {
                if (!enabled(db, PKG) || !scopes(db, PKG, user).containsAll(packages)) throw new IOException("作用域回读不完整");
            }
            state.put("state", "READY").put("added", added);
            // Changed package membership must refresh provider visibility grants at next deployment.
            String oldPackages = "";
            if (report.isFile()) try { oldPackages = new JSONObject(IdentityStore.read(new FileInputStream(report))).optJSONArray("packages").toString(); } catch (Exception ignored) {}
            if (!new JSONArray(packages).toString().equals(oldPackages)) new File(BASE, user + "-" + serial + ".prepared").delete();
            write(report, state);
            System.out.println("空间 " + user + "：作用域已核验（" + packages.size() + " 个应用），等待应用重新加载");
        } catch (Exception e) {
            state.put("state", "PENDING").put("error", e.getMessage()); write(report, state); throw e;
        }
    }

    private static void approve(int user, String pkg) throws Exception {
        ensureSystemContext();
        CountDownLatch received = new CountDownLatch(1);
        String[] outcome = {"作用域回调超时"};
        Binder callback = new Binder() {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { if (reply != null) reply.writeString(CALLBACK); return true; }
                if (Binder.getCallingUid() != 0 && Binder.getCallingUid() != 1000) return false;
                data.enforceInterface(CALLBACK); String returned = data.readString();
                if (!pkg.equals(returned)) return false;
                if (code == FIRST_CALL_TRANSACTION + 2) outcome[0] = "OK";
                else if (code == FIRST_CALL_TRANSACTION + 5) outcome[0] = data.readString();
                else outcome[0] = "LSPosed 未批准作用域（" + code + "）";
                received.countDown(); return true;
            }
        };
        Bundle extras = new Bundle(); extras.putBinder("callback", callback);
        Intent intent = new Intent(action).setPackage("android").setData(new Uri.Builder().scheme("module")
                .encodedAuthority(PKG + ":" + user).appendPath(pkg).appendQueryParameter("action", "approve").build())
                .putExtras(extras).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        context.sendBroadcast(intent);
        if (!received.await(8, TimeUnit.SECONDS) || !"OK".equals(outcome[0])) throw new IOException(outcome[0]);
    }

    private static void ensureSystemContext() throws Exception {
        if (context != null) return;
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        Class<?> thread = Class.forName("android.app.ActivityThread");
        Object main = thread.getMethod("systemMain").invoke(null);
        context = (Context) thread.getMethod("getSystemContext").invoke(main);
    }

    private static void write(File file, JSONObject value) throws Exception {
        AtomicFile atomic = new AtomicFile(file); FileOutputStream out = null;
        try { out = atomic.startWrite(); out.write(value.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out); }
        catch (Exception e) { if (out != null) atomic.failWrite(out); throw e; }
        android.system.Os.chmod(file.getAbsolutePath(), 0600);
    }

    private static String exec(String... command) throws Exception {
        java.lang.Process child = new ProcessBuilder(command).redirectErrorStream(true).start();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            Future<String> output = worker.submit(() -> {
                try (InputStream in = child.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) != -1) { if (out.size() + n > 4194304) throw new IOException("输出过大"); out.write(buffer, 0, n); }
                    return out.toString("UTF-8").trim();
                }
            });
            if (!child.waitFor(15, TimeUnit.SECONDS)) throw new IOException("系统查询超时");
            String result = output.get(2, TimeUnit.SECONDS);
            if (child.exitValue() != 0) throw new IOException(result); return result;
        } finally { child.destroy(); worker.shutdownNow(); }
    }
}
