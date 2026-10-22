package com.captainsss.usershub.probe;

import android.app.Activity;
import android.os.*;
import android.content.Context;
import android.media.MediaDrm;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class ProbeActivity extends Activity {
    private final JSONObject hashes = new JSONObject();
    private interface Read { Object get() throws Exception; }
    private void collect(String key, Read action) throws Exception {
        try { Object v = action.get(); hashes.put(key, digest(v == null ? "<null>" : v.toString())); }
        catch (Throwable e) { hashes.put(key, "UNAVAILABLE:" + e.getClass().getSimpleName()); }
    }
    public static String digest(String value) throws Exception {
        byte[] data = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : data) out.append(String.format(Locale.ROOT, "%02x", b & 255)); return out.toString();
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); TextView text = new TextView(this); text.setTextSize(13); text.setPadding(24,48,24,24);
        ScrollView scroll = new ScrollView(this); scroll.addView(text); setContentView(scroll);
        try {
            TelephonyManager t = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            collect("android_id", () -> Settings.Secure.getString(getContentResolver(), "android_id"));
            collect("gsf_id", () -> Settings.Secure.getString(getContentResolver(), "gsf_id"));
            collect("imei", t::getImei); collect("meid", t::getMeid); collect("imsi", t::getSubscriberId);
            collect("iccid", t::getSimSerialNumber); collect("phone_number", t::getLine1Number);
            collect("gaid", () -> new com.google.android.gms.ads.identifier.AdvertisingIdClient.Info().getId());
            collect("app_set_id", () -> new com.google.android.gms.appset.AppSetIdInfo().getId());
            collect("media_drm_id", () -> {
                try (MediaDrm drm = new MediaDrm(UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"))) {
                    StringBuilder hex = new StringBuilder(); for (byte b : drm.getPropertyByteArray("deviceUniqueId")) hex.append(String.format(Locale.ROOT, "%02x", b & 255)); return hex.toString();
                }
            });
            collect("brand", () -> Build.BRAND); collect("manufacturer", () -> Build.MANUFACTURER);
            collect("model", () -> Build.MODEL); collect("device", () -> Build.DEVICE); collect("product", () -> Build.PRODUCT);
            collect("board", () -> Build.BOARD); collect("hardware", () -> Build.HARDWARE); collect("fingerprint", () -> Build.FINGERPRINT);
            collect("build_id", () -> Build.ID); collect("build_incremental", () -> Build.VERSION.INCREMENTAL);
            collect("build_type", () -> Build.TYPE); collect("build_tags", () -> Build.TAGS);
            collect("serial", Build::getSerial);
            JSONObject result = new JSONObject().put("userId", android.os.Process.myUid() / 100000)
                    .put("hashes", hashes).put("sdkUnchanged", Build.VERSION.SDK_INT == 31)
                    .put("sdkFixtures", "GAID/AppSet use test classes, not live Google services");
            Uri uri = Uri.parse("content://com.captainsss.usershub.identity");
            try {
                getContentResolver().call(uri, "get", "com.example.other", null);
                result.put("crossPackageRejected", false);
            } catch (SecurityException denied) { result.put("crossPackageRejected", true); }
            catch (Exception absent) { result.put("crossPackageRejected", "PROVIDER_UNAVAILABLE"); }
            try {
                Bundle reply = getContentResolver().call(uri, "get", getPackageName(), null);
                result.put("providerState", reply == null ? "MISSING" : reply.getString("state"));
                if (reply != null && reply.containsKey("config")) {
                    JSONObject config = new JSONObject(reply.getString("config")); JSONObject profile = config.getJSONObject("profile");
                    JSONObject values = profile.getJSONObject("values"); JSONArray enabled = profile.getJSONArray("enabledFields");
                    JSONObject checks = new JSONObject();
                    for (int i = 0; i < enabled.length(); i++) {
                        String field = enabled.getString(i); checks.put(field, digest(values.getString(field)).equals(hashes.optString(field)));
                    }
                    result.put("checks", checks).put("revision", config.getString("revision"));
                }
            } catch (Exception unavailable) { result.put("providerState", unavailable.getClass().getSimpleName()); }
            try (FileOutputStream out = openFileOutput("result.json", MODE_PRIVATE)) { out.write(result.toString(2).getBytes(StandardCharsets.UTF_8)); }
            text.setText("UsersHub 接口验证（只记录哈希，不记录真实 ID）\n\n" + result.toString(2));
        } catch (Exception e) { text.setText("验证失败：" + e); }
    }
}
