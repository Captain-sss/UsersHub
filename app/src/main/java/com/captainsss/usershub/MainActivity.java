package com.captainsss.usershub;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout listLayout;
    private TextView rootStatus;
    private TextView summary;
    private ProgressBar progress;

    private static final int BG = Color.rgb(245, 246, 248);
    private static final int GREEN = Color.rgb(31, 157, 83);
    private static final int RED = Color.rgb(190, 50, 50);
    private static final int GRAY = Color.rgb(132, 138, 148);
    private static final int TEXT = Color.rgb(30, 32, 36);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        requestRootAndRefresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(dp(14), dp(18), dp(14), dp(30));
        scroll.addView(rootLayout, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        rootLayout.addView(text("空间冻结控制", 25, true, TEXT));
        TextView subtitle = text("原生 APK · Magisk Root · 深度冻结（含系统应用）", 13, false, Color.DKGRAY);
        subtitle.setPadding(0, dp(4), 0, dp(12));
        rootLayout.addView(subtitle);

        LinearLayout statusCard = verticalCard();
        rootStatus = text("Root：正在请求授权…", 14, true, GRAY);
        statusCard.addView(rootStatus);

        summary = text("正在读取空间状态", 13, false, Color.DKGRAY);
        summary.setPadding(0, dp(8), 0, 0);
        statusCard.addView(summary);

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(24), dp(24));
        pp.topMargin = dp(10);
        progress.setLayoutParams(pp);
        statusCard.addView(progress);
        rootLayout.addView(statusCard);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(8), 0, dp(8));

        Button refresh = actionButton("刷新", Color.WHITE, TEXT);
        refresh.setOnClickListener(v -> requestRootAndRefresh());
        actions.addView(refresh, weighted());

        Button freezeAll = actionButton("全部冻结", Color.rgb(255, 238, 238), RED);
        freezeAll.setOnClickListener(v -> runAction("freeze-all"));
        actions.addView(freezeAll, weighted());

        Button thawAll = actionButton("全部解冻", Color.rgb(232, 248, 238), GREEN);
        thawAll.setOnClickListener(v -> runAction("thaw-all"));
        actions.addView(thawAll, weighted());

        rootLayout.addView(actions);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(listLayout);

        TextView note = text(
                "深度冻结 = suspend 该空间全部第三方 App + 带桌面图标的系统 App（图标变灰），再 stop-user 停止整个用户后台。为保证空间仍能启动和恢复，当前桌面/Home、SystemUI、Android 框架、包管理/权限组件及少量 MIUI 核心组件不会 suspend；这些无图标核心后台仍会随 stop-user 停止。解冻只恢复 UsersHub 自己记录的 App。Shared 由 Magisk 模块维护。",
                12, false, Color.rgb(105, 110, 118));
        note.setPadding(dp(4), dp(12), dp(4), 0);
        rootLayout.addView(note);
        setContentView(scroll);
    }

    private void requestRootAndRefresh() {
        setBusy(true);
        executor.execute(() -> {
            CommandResult id = runSu("id -u");
            if (!id.ok || !id.output.trim().equals("0")) {
                main.post(() -> {
                    rootStatus.setText("Root：未授权或授权失败");
                    rootStatus.setTextColor(RED);
                    summary.setText("请在 Magisk 超级用户中允许 UsersHub，然后点“刷新”。");
                    listLayout.removeAllViews();
                    setBusy(false);
                });
                return;
            }

            CommandResult helper = runSu("command -v usershubctl >/dev/null 2>&1 && usershubctl status --plain || echo __MODULE_MISSING__");
            main.post(() -> {
                rootStatus.setText("Root：已授权");
                rootStatus.setTextColor(GREEN);
                if (helper.output.contains("__MODULE_MISSING__")) {
                    summary.setText("Magisk 模块未安装：请先安装 UsersHub Core 模块并重启。");
                    listLayout.removeAllViews();
                } else if (!helper.ok) {
                    summary.setText("读取状态失败：" + oneLine(helper.output));
                    listLayout.removeAllViews();
                } else {
                    renderStatus(helper.output);
                }
                setBusy(false);
            });
        });
    }

    private void renderStatus(String output) {
        listLayout.removeAllViews();
        List<UserRow> rows = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 6) continue;
            try {
                UserRow r = new UserRow();
                r.id = Integer.parseInt(p[0]);
                r.name = p[1].isEmpty() ? ("空间 " + r.id) : p[1];
                r.frozen = "FROZEN".equals(p[2]);
                r.running = "RUNNING".equals(p[3]);
                r.shared = "SHARED".equals(p[4]);
                r.count = Integer.parseInt(p[5]);
                rows.add(r);
            } catch (Exception ignored) {}
        }

        int frozen = 0, running = 0;
        for (UserRow r : rows) {
            if (r.frozen) frozen++;
            if (r.running) running++;
        }
        summary.setText("空间 " + rows.size() + " 个 · 已深度冻结 " + frozen + " 个 · 当前运行 " + running + " 个");

        if (rows.isEmpty()) {
            TextView none = text("未检测到用户 10 以上的次级空间。", 14, false, GRAY);
            none.setPadding(dp(6), dp(12), dp(6), dp(12));
            listLayout.addView(none);
            return;
        }
        for (UserRow r : rows) listLayout.addView(buildUserCard(r));
    }

    private View buildUserCard(UserRow r) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(13), dp(12), dp(13));
        GradientDrawable bg = rounded(Color.WHITE, 16);
        bg.setStroke(dp(1), Color.rgb(230, 232, 236));
        card.setBackground(bg);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(9);
        card.setLayoutParams(cp);

        TextView dot = text("●", 17, true, r.frozen ? GRAY : GREEN);
        dot.setLayoutParams(new LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(dot);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        info.addView(text("空间 " + r.id + "  ·  " + r.name, 16, true, TEXT));

        String state = r.frozen ? "已深度冻结 · 系统/第三方应用已暂停" : (r.running ? "正常 · 正在运行" : "正常 · 当前未运行");
        if (r.shared) state += " · Shared";
        if (r.frozen) state += " · " + r.count + " 个 App";
        TextView detail = text(state, 12, false, Color.rgb(117, 122, 130));
        detail.setPadding(0, dp(4), 0, 0);
        info.addView(detail);
        card.addView(info);

        Button toggle = actionButton(r.frozen ? "解冻" : "冻结",
                r.frozen ? Color.rgb(231, 248, 237) : Color.rgb(239, 241, 244),
                r.frozen ? GREEN : Color.rgb(77, 84, 94));
        toggle.setOnClickListener(v -> runAction((r.frozen ? "thaw " : "freeze ") + r.id));
        card.addView(toggle, new LinearLayout.LayoutParams(dp(82), dp(44)));
        return card;
    }

    private void runAction(String action) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult result = runSu("usershubctl " + action);
            main.post(() -> {
                Toast.makeText(this,
                        result.ok ? ("完成：" + oneLine(result.output)) : ("失败：" + oneLine(result.output)),
                        Toast.LENGTH_LONG).show();
                setBusy(false);
                requestRootAndRefresh();
            });
        });
    }

    private CommandResult runSu(String command) {
        StringBuilder sb = new StringBuilder();
        int code = -1;
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            if (process.waitFor(300, TimeUnit.SECONDS)) code = process.exitValue();
            else { process.destroyForcibly(); sb.append("timeout after 300s"); }
        } catch (Exception e) {
            sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
        return new CommandResult(code == 0, sb.toString().trim());
    }

    private void setBusy(boolean busy) { progress.setVisibility(busy ? View.VISIBLE : View.GONE); }

    private String oneLine(String s) {
        if (s == null || s.trim().isEmpty()) return "无输出";
        String v = s.trim().replace('\n', ' ').replace('\r', ' ');
        return v.length() > 220 ? v.substring(0, 220) + "…" : v;
    }

    private LinearLayout verticalCard() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(15), dp(14), dp(15), dp(14));
        GradientDrawable bg = rounded(Color.WHITE, 16);
        bg.setStroke(dp(1), Color.rgb(230, 232, 236));
        v.setBackground(bg);
        return v;
    }

    private Button actionButton(String label, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(rounded(bgColor, 11));
        return b;
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class CommandResult {
        final boolean ok;
        final String output;
        CommandResult(boolean ok, String output) { this.ok = ok; this.output = output; }
    }

    private static class UserRow {
        int id;
        String name;
        boolean frozen;
        boolean running;
        boolean shared;
        int count;
    }
}
