package com.captainsss.usershub.identity;

import android.app.Application;
import android.content.Context;
import android.os.*;
import org.json.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Optional process-local simulation. No zygote/system_server hooks, no persistent property writes. */
public final class IdentityHook implements IXposedHookLoadPackage {
    private static final String MODULE = "com.captainsss.usershub";
    private static final String[] SDK_CLASSES = {
            "com.google.android.gms.ads.identifier.AdvertisingIdClient$Info",
            "com.google.android.gms.appset.AppSetIdInfo"};
    private volatile Map<String, String> values = Collections.emptyMap();
    private volatile String revision = "";
    private final Set<String> observed = ConcurrentHashMap.newKeySet();
    private final Set<String> verified = ConcurrentHashMap.newKeySet();
    private final Set<String> mismatched = ConcurrentHashMap.newKeySet();
    private final Set<Class<?>> sdkHooked = Collections.newSetFromMap(new ConcurrentHashMap<Class<?>, Boolean>());
    private final Map<String, Object> originalBuild = new HashMap<>();
    private final Map<String, Object> originalBuildVersion = new HashMap<>();
    private final Map<String, String> properties = new HashMap<>();
    private Context context; private String target;
    private Handler worker; private boolean attached; private String lastReport = "";

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (MODULE.equals(lp.packageName)) {
            XposedHelpers.findAndHookMethod(EngineStatus.class.getName(), lp.classLoader, "isActive",
                    XC_MethodReplacement.returnConstant(true)); return;
        }
        if (lp.appInfo == null || lp.appInfo.uid % 100000 < 10000 || lp.appInfo.uid / 100000 < 10
                || (lp.appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                || !IdentityStore.eligiblePackage(lp.packageName)) return;
        target = lp.packageName;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                if (attached) return; attached = true;
                context = (Context) param.args[0];
                try {
                    captureBuild(); reload(); installHooks(lp.classLoader); applyBuild();
                    HandlerThread thread = new HandlerThread("UsersHub-identity"); thread.start();
                    worker = new Handler(thread.getLooper()); worker.post(poll);
                    XposedBridge.log("UsersHubIdentity: ready user=" + android.os.Process.myUid() / 100000 + " package=" + target);
                } catch (Throwable e) {
                    values = Collections.emptyMap(); applyBuild();
                    XposedBridge.log("UsersHubIdentity: initialization failed " + e.getClass().getSimpleName());
                }
            }
        });
    }

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            reload(); applyBuild(); report();
            worker.postDelayed(this, values.isEmpty() ? 15000 : 5000);
        }
    };

    private void reload() {
        try {
            Bundle reply = context.getContentResolver().call(IdentityProvider.URI, "get", target, null);
            if (reply == null || !reply.containsKey("config")) { clear(); return; }
            JSONObject config = new JSONObject(reply.getString("config"));
            if (config.getInt("userId") != android.os.Process.myUid() / 100000) { clear(); return; }
            String rev = config.getString("revision"); if (rev.equals(revision)) return;
            JSONObject profile = config.getJSONObject("profile"); JSONObject source = profile.getJSONObject("values");
            JSONArray enabled = profile.getJSONArray("enabledFields"); Map<String, String> next = new HashMap<>();
            for (int i = 0; i < enabled.length(); i++) {
                String key = enabled.getString(i), value = source.getString(key);
                IdentityFields.validate(key, value); next.put(key, value);
            }
            observed.clear(); verified.clear(); mismatched.clear();
            values = Collections.unmodifiableMap(next); revision = rev; lastReport = "";
        } catch (Throwable e) { clear(); }
    }

    private void clear() {
        values = Collections.emptyMap(); revision = ""; observed.clear(); verified.clear(); mismatched.clear(); lastReport = "";
    }

    private String value(String key) {
        String value = values.get(key); if (value != null) observed.add(key); return value;
    }

    private void report() {
        if (revision.isEmpty()) return;
        ArrayList<String> fields = new ArrayList<>(observed); Collections.sort(fields);
        ArrayList<String> checked = new ArrayList<>(verified), conflicts = new ArrayList<>(mismatched);
        Collections.sort(checked); Collections.sort(conflicts);
        String signature = revision + fields + checked + conflicts;
        if (signature.equals(lastReport)) return;
        try {
            Bundle payload = new Bundle(); payload.putString("revision", revision); payload.putStringArrayList("observed", fields);
            payload.putStringArrayList("verified", checked); payload.putStringArrayList("mismatched", conflicts);
            Bundle result = context.getContentResolver().call(IdentityProvider.URI, "report", target, payload);
            if (result != null && "RECORDED".equals(result.getString("state"))) lastReport = signature;
        } catch (Throwable ignored) {}
    }

    private void captureBuild() {
        for (String key : IdentityFields.BUILD) {
            try {
                if (IdentityFields.isBuildVersionField(key))
                    originalBuildVersion.put(key, XposedHelpers.getStaticObjectField(Build.VERSION.class, "INCREMENTAL"));
                else {
                    String field = IdentityFields.buildField(key);
                    if (field != null) originalBuild.put(key, XposedHelpers.getStaticObjectField(Build.class, field));
                }
            }
            catch (Throwable ignored) {}
        }
        for (String key : new String[]{"brand", "manufacturer", "model", "device", "product"}) {
            String name = key.equals("product") ? "name" : key;
            properties.put("ro.product." + name, key);
            for (String part : new String[]{"system", "vendor", "product", "odm", "system_ext", "bootimage"})
                properties.put("ro.product." + part + "." + name, key);
        }
        properties.put("ro.product.board", "board"); properties.put("ro.hardware", "hardware");
        properties.put("ro.build.fingerprint", "fingerprint"); properties.put("ro.serialno", "serial");
        for (String part : new String[]{"system", "vendor", "product", "odm", "system_ext", "bootimage"})
            properties.put("ro." + part + ".build.fingerprint", "fingerprint");
        String[][] companions = {{"id", "build_id"}, {"version.incremental", "build_incremental"},
                {"type", "build_type"}, {"tags", "build_tags"}};
        for (String[] companion : companions) {
            properties.put("ro.build." + companion[0], companion[1]);
            for (String part : new String[]{"system", "vendor", "product", "odm", "system_ext", "bootimage"})
                properties.put("ro." + part + ".build." + companion[0], companion[1]);
        }
    }

    private synchronized void applyBuild() {
        for (Map.Entry<String, Object> entry : originalBuild.entrySet()) {
            try {
                String desired = values.get(entry.getKey());
                XposedHelpers.setStaticObjectField(Build.class, IdentityFields.buildField(entry.getKey()),
                        desired == null ? entry.getValue() : desired);
            } catch (Throwable ignored) {}
        }
        for (Map.Entry<String, Object> entry : originalBuildVersion.entrySet()) {
            try {
                String desired = values.get(entry.getKey());
                XposedHelpers.setStaticObjectField(Build.VERSION.class, "INCREMENTAL",
                        desired == null ? entry.getValue() : desired);
            } catch (Throwable ignored) {}
        }
    }

    private void installHooks(ClassLoader loader) {
        hook("android.provider.Settings$Secure", loader, "getString", new VerifiedHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length < 2) return;
                String key = "android_id".equals(p.args[1]) ? "android_id" : "gsf_id".equals(p.args[1]) ? "gsf_id" : null;
                if (key != null) { String v = value(key); if (v != null) replace(p, key, v); }
            }
        });
        String[][] telephony = {{"getImei", "imei"}, {"getDeviceId", "imei"}, {"getMeid", "meid"},
                {"getSubscriberId", "imsi"}, {"getSimSerialNumber", "iccid"}, {"getLine1Number", "phone_number"}};
        for (String[] pair : telephony) hookString("android.telephony.TelephonyManager", loader, pair[0], pair[1]);
        hookString("android.telephony.SubscriptionInfo", loader, "getIccId", "iccid");
        hookString("android.telephony.SubscriptionInfo", loader, "getNumber", "phone_number");
        hookString("android.os.Build", loader, "getSerial", "serial");
        hook("android.media.MediaDrm", loader, "getPropertyByteArray", new VerifiedHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length != 1 || !"deviceUniqueId".equals(p.args[0])) return;
                String v = value("media_drm_id"); if (v == null) return;
                byte[] bytes = new byte[v.length() / 2];
                for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) Integer.parseInt(v.substring(i * 2, i * 2 + 2), 16);
                replace(p, "media_drm_id", bytes);
            }
        });
        hook("android.os.SystemProperties", loader, "get", new VerifiedHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.args.length == 0) return; String key = properties.get(p.args[0]);
                if (key != null) { String v = value(key); if (v != null) replace(p, key, v); }
            }
        });
        for (String name : SDK_CLASSES) {
            try { sdkClass(Class.forName(name, false, loader)); } catch (Throwable ignored) {}
        }
        // SDK classes may arrive later via split APKs/dynamic class loaders.
        XposedBridge.hookAllMethods(ClassLoader.class, "loadClass", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.args.length == 0 || p.hasThrowable()) return;
                for (String name : SDK_CLASSES) if (name.equals(p.args[0]) && p.getResult() instanceof Class<?>) sdkClass((Class<?>) p.getResult());
            }
        });
    }

    private void sdkClass(Class<?> cls) {
        if (!sdkHooked.add(cls)) return;
        String key = cls.getName().contains("AdvertisingIdClient") ? "gaid" : "app_set_id";
        hookClass(cls, "getId", stringCallback(key));
    }
    private XC_MethodHook stringCallback(final String key) {
        return new VerifiedHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.method instanceof Method && ((Method) p.method).getReturnType() == String.class) {
                    String v = value(key); if (v != null) replace(p, key, v);
                }
            }
        };
    }
    private void replace(XC_MethodHook.MethodHookParam p, String key, Object replacement) {
        p.setObjectExtra("usershub.field", key);
        p.setObjectExtra("usershub.expected", replacement);
        p.setObjectExtra("usershub.revision", revision);
        p.setResult(replacement);
    }
    private abstract class VerifiedHook extends XC_MethodHook {
        @Override protected void afterHookedMethod(MethodHookParam p) {
            String key = (String) p.getObjectExtra("usershub.field");
            if (key == null || !revision.equals(p.getObjectExtra("usershub.revision"))) return;
            Object expected = p.getObjectExtra("usershub.expected"), actual = p.getResult();
            boolean matches = !p.hasThrowable() && (expected instanceof byte[] && actual instanceof byte[]
                    ? Arrays.equals((byte[]) expected, (byte[]) actual) : Objects.equals(expected, actual));
            if (matches) verified.add(key); else mismatched.add(key);
        }
    }
    private void hookString(String cls, ClassLoader loader, String method, String key) { hook(cls, loader, method, stringCallback(key)); }
    private void hook(String cls, ClassLoader loader, String method, XC_MethodHook callback) {
        try { hookClass(Class.forName(cls, false, loader), method, callback); } catch (Throwable ignored) {}
    }
    private void hookClass(Class<?> cls, String method, XC_MethodHook callback) {
        try { XposedBridge.hookAllMethods(cls, method, callback); } catch (Throwable ignored) {}
    }
}
