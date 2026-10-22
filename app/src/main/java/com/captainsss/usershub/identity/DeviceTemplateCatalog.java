package com.captainsss.usershub.identity;

import android.content.Context;
import android.os.Build;
import org.json.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.regex.*;

/** Validated, source-attributed device model templates bundled with the APK. */
public final class DeviceTemplateCatalog {
    private static final String ASSET = "device_templates.json";
    private static final Set<String> VERIFICATION = new HashSet<>(Arrays.asList("official", "community", "review"));
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,60}");
    private static final Pattern FINGERPRINT = Pattern.compile(
            "^([^/\\s]+)/([^/\\s]+)/([^:\\s]+):([^/\\s]+)/([^/\\s]+)/([^:\\s]+):([^/\\s]+)/([^/\\s]+)$");
    private static final String[] REQUIRED = {"brand", "manufacturer", "model", "device", "product", "fingerprint"};
    private static final String[] COMPLETE = {"brand", "manufacturer", "model", "device", "product", "hardware", "fingerprint"};
    private static final SecureRandom RANDOM = new SecureRandom();

    private DeviceTemplateCatalog() {}

    public static final class Template {
        public final String id, name, verification, sourceName, sourceUrl, note;
        public final boolean randomEligible;
        public final Map<String, String> values;

        private Template(String id, String name, String verification, boolean randomEligible,
                         String sourceName, String sourceUrl, String note, Map<String, String> values) {
            this.id = id; this.name = name; this.verification = verification; this.randomEligible = randomEligible;
            this.sourceName = sourceName; this.sourceUrl = sourceUrl; this.note = note;
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        public String badge() {
            if ("official".equals(verification)) return "官方来源";
            if ("review".equals(verification)) return "待核验";
            return "社区来源";
        }

        public String listLabel() {
            return name + " · Android " + androidRelease() + "\n" + badge() + " · " + values.size() + " 项"
                    + (randomEligible ? " · 可随机" : " · 仅手动");
        }

        public String androidRelease() {
            Matcher matcher = FINGERPRINT.matcher(values.get("fingerprint"));
            return matcher.matches() ? matcher.group(4) : "未知";
        }

        public int androidMajor() { return DeviceTemplateCatalog.androidMajor(androidRelease()); }

        public boolean compatibleWithCurrentDevice() {
            return androidMajor() == DeviceTemplateCatalog.androidMajor(Build.VERSION.RELEASE);
        }

        public String searchText() {
            return (name + " " + values.get("brand") + " " + values.get("manufacturer") + " "
                    + values.get("model") + " " + values.get("device") + " Android " + androidRelease())
                    .toLowerCase(Locale.ROOT);
        }
    }

    public static List<Template> load(Context context) throws Exception {
        return parse(IdentityStore.read(context.getAssets().open(ASSET)));
    }

    static List<Template> parse(String source) throws Exception {
        JSONObject root = new JSONObject(source);
        if (root.optInt("version", -1) != 1) throw new IllegalArgumentException("机型模板版本不受支持");
        JSONArray array = root.optJSONArray("templates");
        if (array == null || array.length() == 0 || array.length() > 100)
            throw new IllegalArgumentException("机型模板数量无效");
        Set<String> ids = new HashSet<>(); List<Template> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i); String id = text(item, "id", 60);
            if (!ID.matcher(id).matches() || !ids.add(id)) throw new IllegalArgumentException("机型模板 ID 无效或重复：" + id);
            String name = text(item, "name", 100); String verification = text(item, "verification", 20);
            if (!VERIFICATION.contains(verification)) throw new IllegalArgumentException("机型模板核验状态无效：" + id);
            String sourceName = text(item, "sourceName", 100); String sourceUrl = text(item, "sourceUrl", 500);
            if (!sourceUrl.startsWith("https://")) throw new IllegalArgumentException("机型模板来源必须使用 HTTPS：" + id);
            String note = text(item, "note", 500); boolean randomEligible = item.optBoolean("randomEligible", false);
            JSONObject rawValues = item.getJSONObject("values"); Map<String, String> values = new LinkedHashMap<>();
            Iterator<String> keys = rawValues.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!contains(IdentityFields.BUILD, key) || "serial".equals(key))
                    throw new IllegalArgumentException("机型模板包含不允许的字段：" + key);
            }
            for (String key : IdentityFields.BUILD) if (rawValues.has(key)) {
                String value = rawValues.getString(key).trim(); IdentityFields.validate(key, value); values.put(key, value);
            }
            for (String key : REQUIRED) if (!values.containsKey(key))
                throw new IllegalArgumentException("机型模板缺少字段 " + key + "：" + id);
            Matcher fingerprint = FINGERPRINT.matcher(values.get("fingerprint"));
            if (!fingerprint.matches()) throw new IllegalArgumentException("构建指纹格式不完整：" + id);
            if (!fingerprint.group(1).equalsIgnoreCase(values.get("brand")))
                throw new IllegalArgumentException("构建指纹品牌与模板不一致：" + id);
            if (!fingerprint.group(2).equalsIgnoreCase(values.get("product")))
                throw new IllegalArgumentException("构建指纹产品代号与模板不一致：" + id);
            if (!fingerprint.group(3).equalsIgnoreCase(values.get("device")))
                throw new IllegalArgumentException("构建指纹设备代号与模板不一致：" + id);
            values.put("build_id", fingerprint.group(5));
            values.put("build_incremental", fingerprint.group(6));
            values.put("build_type", fingerprint.group(7));
            values.put("build_tags", fingerprint.group(8));
            for (String key : new String[]{"build_id", "build_incremental", "build_type", "build_tags"})
                IdentityFields.validate(key, values.get(key));
            if (randomEligible) {
                if ("review".equals(verification) || androidMajor(fingerprint.group(4)) < 10)
                    throw new IllegalArgumentException("随机机型必须已通过校验且 Android 版本不低于 10：" + id);
                for (String key : COMPLETE) if (!values.containsKey(key))
                    throw new IllegalArgumentException("随机成套机型缺少字段 " + key + "：" + id);
            }
            result.add(new Template(id, name, verification, randomEligible, sourceName, sourceUrl, note, values));
        }
        return Collections.unmodifiableList(result);
    }

    public static Template currentDevice() {
        Map<String, String> values = new LinkedHashMap<>();
        put(values, "brand", Build.BRAND); put(values, "manufacturer", Build.MANUFACTURER);
        put(values, "model", Build.MODEL); put(values, "device", Build.DEVICE); put(values, "product", Build.PRODUCT);
        put(values, "board", Build.BOARD); put(values, "hardware", Build.HARDWARE); put(values, "fingerprint", Build.FINGERPRINT);
        put(values, "build_id", Build.ID); put(values, "build_incremental", Build.VERSION.INCREMENTAL);
        put(values, "build_type", Build.TYPE); put(values, "build_tags", Build.TAGS);
        return new Template("current_device", "本机实时信息", "official", false, "当前手机系统", "",
                "直接读取当前手机的 Build 信息，不包含序列号。", values);
    }

    public static Template randomComplete(List<Template> templates) {
        return randomComplete(templates, androidMajor(Build.VERSION.RELEASE));
    }

    static Template randomComplete(List<Template> templates, int hostAndroidMajor) {
        List<Template> eligible = new ArrayList<>();
        for (Template template : templates)
            if (template.randomEligible && template.androidMajor() == hostAndroidMajor) eligible.add(template);
        if (eligible.isEmpty()) throw new IllegalStateException("没有与当前 Android 版本一致的成套机型模板");
        return eligible.get(RANDOM.nextInt(eligible.size()));
    }

    public static int compatibleRandomCount(List<Template> templates) {
        int host = androidMajor(Build.VERSION.RELEASE), count = 0;
        for (Template template : templates) if (template.randomEligible && template.androidMajor() == host) count++;
        return count;
    }

    public static Template match(List<Template> templates, JSONObject values) {
        for (Template template : templates) {
            boolean same = true;
            for (String key : IdentityFields.BUILD) if (!"serial".equals(key)) {
                String expected = template.values.get(key); String actual = values.optString(key);
                if (expected == null ? !actual.isEmpty() : !expected.equals(actual)) { same = false; break; }
            }
            if (same) return template;
        }
        return null;
    }

    private static String text(JSONObject object, String key, int max) {
        String value = object.optString(key, "").trim();
        if (value.isEmpty() || value.length() > max || value.matches("(?s).*[\\r\\n\\x00].*"))
            throw new IllegalArgumentException("机型模板字段无效：" + key);
        return value;
    }
    private static boolean contains(String[] values, String target) { for (String value : values) if (value.equals(target)) return true; return false; }
    private static int androidMajor(String release) {
        try { return Integer.parseInt(release.split("\\.")[0]); } catch (Exception ignored) { return -1; }
    }
    private static void put(Map<String, String> values, String key, String value) { if (value != null && !value.trim().isEmpty()) values.put(key, value.trim()); }
}
