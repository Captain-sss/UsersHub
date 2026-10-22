package com.captainsss.usershub.identity;

import java.util.*;
import java.util.regex.*;

/** LSPosed 1.9.2 receiver shape. Ambiguous/changed layouts fail closed. */
public final class ScopeProtocol {
    public static String approvalAction(String dump) {
        Set<String> actions = new HashSet<>();
        for (String block : dump.split("\\* ReceiverList\\{")) {
            if (!block.contains("uid=1000 user=0") || !block.contains("Scheme: \"module\"")) continue;
            if (!block.contains("requiredPermission=android.permission.BRICK") || block.contains("Filter #1:")) continue;
            Matcher m = Pattern.compile("Action: \"([0-9a-f]{8}-[0-9a-f-]{27})\"").matcher(block);
            if (m.find()) actions.add(m.group(1));
        }
        if (actions.size() != 1) throw new IllegalStateException("无法唯一识别 LSPosed 作用域接口，请手动检查作用域");
        return actions.iterator().next();
    }
}
