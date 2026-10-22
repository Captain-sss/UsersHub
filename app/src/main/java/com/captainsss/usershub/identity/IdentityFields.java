package com.captainsss.usershub.identity;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Synthetic test values only; these do not alter the modem, SIM, or attestation keys. */
public final class IdentityFields {
    public static final String[] IDS = {"android_id", "imei", "meid", "imsi", "iccid",
            "phone_number", "gaid", "gsf_id", "media_drm_id", "app_set_id"};
    public static final String[] ID_LABELS = {"Android ID", "IMEI（接口模拟）", "MEID（接口模拟）",
            "IMSI（SIM 接口模拟）", "ICCID（SIM 接口模拟）", "电话号码（模拟值）", "Google 广告 ID",
            "GSF ID（部分接口）", "Media DRM ID（非硬件证明）", "App Set ID"};
    public static final String[] BUILD = {"brand", "manufacturer", "model", "device", "product",
            "board", "hardware", "fingerprint", "build_id", "build_incremental", "build_type",
            "build_tags", "serial"};
    public static final String[] BUILD_LABELS = {"品牌", "厂商", "型号", "设备代号", "产品代号",
            "主板", "硬件平台", "系统构建指纹", "构建 ID", "增量版本", "构建类型",
            "签名标签", "序列号（接口模拟）"};
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdentityFields() {}

    public static String label(String key) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(key)) return ID_LABELS[i];
        for (int i = 0; i < BUILD.length; i++) if (BUILD[i].equals(key)) return BUILD_LABELS[i];
        return key;
    }

    public static boolean known(String key) {
        for (String s : IDS) if (s.equals(key)) return true;
        for (String s : BUILD) if (s.equals(key)) return true;
        return false;
    }

    /** Returns the android.os.Build static field represented by a profile key. */
    public static String buildField(String key) {
        switch (key) {
            case "brand": case "manufacturer": case "model": case "device": case "product":
            case "board": case "hardware": case "fingerprint": case "serial":
                return key.toUpperCase(java.util.Locale.ROOT);
            case "build_id": return "ID";
            case "build_type": return "TYPE";
            case "build_tags": return "TAGS";
            default: return null;
        }
    }

    public static boolean isBuildVersionField(String key) { return "build_incremental".equals(key); }

    public static String generate(String key) {
        switch (key) {
            case "android_id": case "gsf_id": return hex(8);
            case "meid": return hex(7).toUpperCase(java.util.Locale.ROOT);
            case "imei": return luhn("35847631" + digits(6));
            case "imsi": return "310260" + digits(9);
            case "iccid": return luhn("8901260" + digits(11));
            case "phone_number": return "+1" + (200 + RANDOM.nextInt(800)) + digits(7);
            case "gaid": case "app_set_id": return UUID.randomUUID().toString();
            case "media_drm_id": return hex(32);
            default: throw new IllegalArgumentException("不能随机生成此字段：" + key);
        }
    }

    public static Map<String, String> generateAll() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String key : IDS) out.put(key, generate(key));
        return out;
    }

    public static void validate(String key, String value) {
        if (!known(key)) throw new IllegalArgumentException("未知字段：" + key);
        if (value == null || value.isEmpty() || value.length() > 300 || value.matches("(?s).*[\\r\\n\\x00].*"))
            throw new IllegalArgumentException("字段为空或格式无效：" + key);
        String regex;
        switch (key) {
            case "android_id": case "gsf_id": regex = "[0-9a-fA-F]{16}"; break;
            case "imei": regex = "[0-9]{15}"; break;
            case "meid": regex = "[0-9a-fA-F]{14}"; break;
            case "imsi": regex = "[0-9]{15}"; break;
            case "iccid": regex = "[0-9]{19,20}"; break;
            case "phone_number": regex = "\\+?[0-9]{7,15}"; break;
            case "gaid": case "app_set_id":
                regex = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}"; break;
            case "media_drm_id": regex = "[0-9a-fA-F]{64}"; break;
            case "build_id": case "build_incremental": regex = "[A-Za-z0-9._-]{1,120}"; break;
            case "build_type": regex = "[A-Za-z0-9._-]{1,40}"; break;
            case "build_tags": regex = "[A-Za-z0-9._,-]{1,120}"; break;
            default: regex = "[^\\r\\n]+";
        }
        if (!value.matches(regex)) throw new IllegalArgumentException("字段格式错误：" + key);
        if ((key.equals("imei") || key.equals("iccid")) && !luhn(value.substring(0, value.length() - 1)).equals(value))
            throw new IllegalArgumentException("校验位错误：" + key);
    }

    public static String luhn(String prefix) {
        int sum = 0;
        boolean twice = true;
        for (int i = prefix.length() - 1; i >= 0; i--, twice = !twice) {
            int n = prefix.charAt(i) - '0';
            if (twice) { n *= 2; if (n > 9) n -= 9; }
            sum += n;
        }
        return prefix + ((10 - sum % 10) % 10);
    }

    private static String digits(int count) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < count; i++) s.append(RANDOM.nextInt(10));
        return s.toString();
    }

    private static String hex(int bytes) {
        byte[] data = new byte[bytes]; RANDOM.nextBytes(data);
        StringBuilder s = new StringBuilder();
        for (byte b : data) s.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return s.toString();
    }
}
