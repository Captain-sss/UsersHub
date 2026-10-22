package com.captainsss.usershub.identity;

import com.captainsss.usershub.RootCommand;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class IdentityBackend {
    public static final class Space {
        public final int id; public final long serial;
        Space(int id, long serial) { this.id = id; this.serial = serial; }
        @Override public String toString() { return "空间 " + id; }
    }
    public static List<Space> spaces() throws Exception {
        RootCommand.Result r = RootCommand.run("usershubidentity list");
        if (!r.ok) throw new Exception("需要 UsersHub Core 1.8.0：" + r.output);
        List<Space> out = new ArrayList<>();
        for (String line : r.output.split("\\R")) {
            if (!line.matches("[0-9]+\\|[0-9]+")) continue;
            String[] f = line.split("\\|"); out.add(new Space(Integer.parseInt(f[0]), Long.parseLong(f[1])));
        }
        Collections.sort(out, Comparator.comparingInt(s -> s.id)); return out;
    }
    public static String deploy(JSONObject workspace) throws Exception {
        IdentityStore.validateWorkspace(workspace);
        RootCommand.Result r = RootCommand.run("usershubidentity stage " + workspace.getInt("userId") + " "
                + workspace.getLong("serial") + " " + RootCommand.quote(workspace.getString("revision")) + " "
                + (workspace.getBoolean("enabled") ? "1" : "0"),
                workspace.toString().getBytes(StandardCharsets.UTF_8));
        if (!r.ok) throw new Exception(r.output);
        StringBuilder message = new StringBuilder();
        for (String line : r.output.split("\\R")) {
            String[] parts = line.split("\\|", 4);
            if (message.length() > 0) message.append("\n");
            message.append(parts.length == 4 ? "空间 " + parts[1] + "：" + describeState(parts[0]) : line);
        }
        return message.toString();
    }
    public static String describeState(String state) {
        switch (state.trim()) {
            case "DEPLOYED": return "设备信息已送达；正在运行的应用需重启后读取";
            case "PENDING": return "设备信息已保存；空间正常启动后自动应用";
            case "DISABLED": return "设备信息已保存；设备模拟当前已关闭";
            case "PAUSED": return "设备模拟已紧急停用";
            case "NOT_DEPLOYED": return "设备信息尚未应用";
            default: return state;
        }
    }
    public static List<String> apps(int user) throws Exception {
        RootCommand.Result r = RootCommand.run("pm list packages -3 --user " + user);
        if (!r.ok) throw new Exception(r.output);
        List<String> out = new ArrayList<>();
        for (String line : r.output.split("\\R")) {
            if (!line.startsWith("package:")) continue;
            String p = line.substring(8).trim();
            if (IdentityStore.eligiblePackage(p)) out.add(p);
        }
        Collections.sort(out); return out;
    }
}
