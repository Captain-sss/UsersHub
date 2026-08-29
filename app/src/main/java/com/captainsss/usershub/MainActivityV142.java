package com.captainsss.usershub;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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

public class MainActivityV142 extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout listLayout;
    private TextView rootStatus;
    private TextView summary;
    private TextView totalValue;
    private TextView frozenValue;
    private TextView runningValue;
    private TextView sharedValue;
    private ProgressBar busyIndicator;
    private boolean refreshing;

    private static final int BG = Color.rgb(247, 249, 252);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(25, 29, 36);
    private static final int MUTED = Color.rgb(118, 126, 139);
    private static final int BORDER = Color.rgb(228, 233, 240);
    private static final int BLUE = Color.rgb(47, 104, 196);
    private static final int BLUE_BG = Color.rgb(235, 243, 255);
    private static final int GREEN = Color.rgb(24, 153, 87);
    private static final int GREEN_BG = Color.rgb(232, 248, 239);
    private static final int RED = Color.rgb(188, 55, 63);
    private static final int RED_BG = Color.rgb(255, 238, 239);
    private static final int ORANGE = Color.rgb(205, 128, 31);
    private static final int ORANGE_BG = Color.rgb(255, 246, 229);
    private static final int GRAY = Color.rgb(142, 150, 163);
    private static final int GRAY_BG = Color.rgb(240, 242, 246);

    private final Runnable autoRefresh = this::refresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        refresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout page = column();
        page.setPadding(dp(12), dp(18), dp(12), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = label("UsersHub  1.4.2", 12, true, BLUE);
        brand.setLetterSpacing(0.06f);
        page.addView(brand);

        TextView title = label("空间控制", 28, true, TEXT);
        title.setPadding(0, dp(4), 0, 0);
        page.addView(title);

        TextView sub = label("真实多用户 · 秒停深冻 · 自动 Shared", 13, false, MUTED);
        sub.setPadding(0, dp(3), 0, dp(12));
        page.addView(sub);

        LinearLayout hero = card(18, 15);
        LinearLayout rootLine = row();
        rootLine.setGravity(Gravity.CENTER_VERTICAL);
        rootStatus = pill("Root 连接中", ORANGE_BG, ORANGE);
        rootLine.addView(rootStatus);
        busyIndicator = new ProgressBar(this);
        LinearLayout.LayoutParams busyLp = new LinearLayout.LayoutParams(dp(19), dp(19));
        busyLp.leftMargin = dp(8);
        rootLine.addView(busyIndicator, busyLp);
        hero.addView(rootLine);

        summary = label("正在读取空间状态…", 12, false, MUTED);
        summary.setPadding(0, dp(10), 0, dp(11));
        hero.addView(summary);

        LinearLayout stats = row();
        totalValue = addStat(stats, "空间");
        frozenValue = addStat(stats, "冻结");
        runningValue = addStat(stats, "运行");
        sharedValue = addStat(stats, "Shared");
        hero.addView(stats);
        page.addView(hero);

        LinearLayout actions1 = row();
        actions1.setPadding(0, dp(10), 0, dp(4));
        Button freezeAll = action("全部冻结", RED_BG, RED);
        freezeAll.setOnClickListener(v -> runAction("usershubctl freeze-all", "所有空间已进入冻结流程"));
        actions1.addView(freezeAll, weightedButton());
        Button thawAll = action("全部解冻", GREEN_BG, GREEN);
        thawAll.setOnClickListener(v -> runAction("usershubctl thaw-all", "全部空间解冻完成"));
        actions1.addView(thawAll, weightedButton());
        page.addView(actions1);

        LinearLayout actions2 = row();
        actions2.setPadding(0, dp(4), 0, dp(14));
        Button refresh = action("刷新", Color.WHITE, TEXT);
        stroke(refresh);
        refresh.setOnClickListener(v -> refresh());
        actions2.addView(refresh, weightedButton());
        Button create = action("＋ 创建空间", BLUE_BG, BLUE);
        create.setOnClickListener(v -> showCreateDialog());
        actions2.addView(create, weightedButton());
        page.addView(actions2);

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = label("空间列表", 17, true, TEXT);
        header.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView tip = label("状态自动刷新", 10, false, MUTED);
        header.addView(tip);
        page.addView(header);

        listLayout = column();
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = dp(8);
        page.addView(listLayout, listLp);

        TextView foot = label(
                "冻结会先停止整个 Android User，再在后台补齐灰色图标。Shared 只属于真正运行中的空间。新建空间会先完成一次系统初始化，避免出现“用户已创建但桌面没有正常识别”的情况。",
                10, false, MUTED);
        foot.setPadding(dp(2), dp(10), dp(2), 0);
        page.addView(foot);

        setContentView(scroll);
    }

    private void showCreateDialog() {
        LinearLayout box = column();
        box.setPadding(dp(18), 0, dp(18), 0);

        TextView hint = label("输入空间名称。Android 会自动分配 User ID。", 12, false, MUTED);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText input = new EditText(this);
        input.setHint("例如：工作A / 店铺01");
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("创建新空间")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("仅创建", null)
                .setPositiveButton("创建并启动", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = normalizeName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError("请输入空间名称");
                    return;
                }
                dialog.dismiss();
                runAction("usershubcreate create-start " + shellQuote(name), "空间已创建并初始化");
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String name = normalizeName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError("请输入空间名称");
                    return;
                }
                dialog.dismiss();
                runAction("usershubcreate create " + shellQuote(name), "空间已创建、初始化并停止");
            });
        });
        dialog.show();
    }

    private void refresh() {
        if (refreshing) return;
        refreshing = true;
        main.removeCallbacks(autoRefresh);
        setBusy(true);

        executor.execute(() -> {
            CommandResult root = runSu("id -u");
            if (!root.ok || !"0".equals(root.output.trim())) {
                main.post(() -> {
                    rootStatus.setText("Root 未授权");
                    stylePill(rootStatus, RED_BG, RED);
                    summary.setText("请在 Magisk 超级用户中允许 UsersHub。");
                    listLayout.removeAllViews();
                    setStats("-", "-", "-", "-");
                    refreshing = false;
                    setBusy(false);
                });
                return;
            }

            CommandResult status = runSu(
                    "if command -v usershubstatus >/dev/null 2>&1 && command -v usershubcreate >/dev/null 2>&1; then usershubstatus --plain; else echo __MODULE_MISSING__; fi");
            main.post(() -> {
                rootStatus.setText("Root 已连接");
                stylePill(rootStatus, GREEN_BG, GREEN);
                if (status.output.contains("__MODULE_MISSING__")) {
                    summary.setText("请安装 UsersHub Core v1.4.2 并重启。");
                    listLayout.removeAllViews();
                } else if (!status.ok) {
                    summary.setText("读取失败：" + oneLine(status.output));
                    listLayout.removeAllViews();
                } else {
                    render(status.output);
                }
                refreshing = false;
                setBusy(false);
            });
        });
    }

    private void render(String output) {
        listLayout.removeAllViews();
        List<UserRow> users = new ArrayList<>();
        boolean processing = false;

        for (String line : output.split("\\r?\\n")) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 6) continue;
            try {
                UserRow r = new UserRow();
                r.id = Integer.parseInt(p[0]);
                r.name = p[1].trim();
                r.state = p[2];
                r.running = "RUNNING".equals(p[3]);
                r.shared = "SHARED".equals(p[4]);
                r.count = safeInt(p[5]);
                r.done = p.length > 6 ? safeInt(p[6]) : r.count;
                r.total = p.length > 7 ? safeInt(p[7]) : r.count;
                r.fail = p.length > 8 ? safeInt(p[8]) : 0;
                users.add(r);
                processing |= r.processing();
            } catch (Exception ignored) {}
        }

        int frozen = 0, running = 0, shared = 0;
        for (UserRow r : users) {
            if (r.frozenLike()) frozen++;
            if (r.running) running++;
            if (r.shared) shared++;
        }
        setStats(String.valueOf(users.size()), String.valueOf(frozen), String.valueOf(running), String.valueOf(shared));

        summary.setText(processing
                ? "后台正在补齐冻结/解冻状态，界面会自动刷新"
                : "状态已同步 · 运行数量只统计真正可用的空间");

        if (users.isEmpty()) {
            LinearLayout empty = card(16, 14);
            empty.addView(label("暂无次级空间", 15, true, TEXT));
            TextView x = label("点“创建空间”即可新建真实 Android Full User。", 11, false, MUTED);
            x.setPadding(0, dp(4), 0, 0);
            empty.addView(x);
            listLayout.addView(empty);
        } else {
            for (UserRow r : users) listLayout.addView(userCard(r));
        }

        if (processing) main.postDelayed(autoRefresh, 1400);
    }

    private View userCard(UserRow r) {
        LinearLayout card = card(17, 12);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        card.setLayoutParams(cardLp);

        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView id = label(String.valueOf(r.id), 12, true, Color.WHITE);
        id.setGravity(Gravity.CENTER);
        int idColor = r.isFreezing() ? ORANGE : r.isThawing() ? BLUE : r.isFrozen() ? GRAY : GREEN;
        id.setBackground(round(idColor, 22));
        top.addView(id, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout info = column();
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = dp(10);
        top.addView(info, infoLp);

        String raw = r.name == null ? "" : r.name.trim();
        final String display = (raw.isEmpty() || raw.equals(String.valueOf(r.id))) ? "空间 " + r.id : raw;
        TextView name = label(display, 16, true, TEXT);
        name.setMaxLines(1);
        info.addView(name);
        TextView meta = label("ID " + r.id + " · " + stateText(r), 10, false, MUTED);
        meta.setPadding(0, dp(2), 0, 0);
        info.addView(meta);

        Button toggle;
        if (r.processing()) {
            toggle = action(r.isFreezing() ? "冻结中" : "解冻中", GRAY_BG, GRAY);
            toggle.setEnabled(false);
        } else if (r.isFrozen()) {
            toggle = action("解冻", GREEN_BG, GREEN);
            toggle.setOnClickListener(v -> runAction("usershubctl thaw " + r.id, display + " 已解冻"));
        } else {
            toggle = action("冻结", GRAY_BG, Color.rgb(73, 81, 94));
            toggle.setOnClickListener(v -> runAction("usershubctl freeze " + r.id, display + " 已进入冻结流程"));
        }
        top.addView(toggle, new LinearLayout.LayoutParams(dp(72), dp(40)));
        card.addView(top);

        TextView detail = label(detailText(r), 11, false, MUTED);
        detail.setPadding(dp(48), dp(7), 0, 0);
        card.addView(detail);

        if (r.isFreezing() && r.total > 0) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(r.total);
            bar.setProgress(Math.min(r.done, r.total));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
            bp.leftMargin = dp(48);
            bp.topMargin = dp(7);
            card.addView(bar, bp);
        }
        return card;
    }

    private String stateText(UserRow r) {
        if (r.isFreezing()) return "冻结中";
        if (r.isThawing()) return "解冻中";
        if (r.isFrozen()) return "已冻结";
        if (r.running) return r.shared ? "运行中 · Shared" : "运行中";
        return "未运行";
    }

    private String detailText(UserRow r) {
        if (r.isFreezing()) {
            return r.total > 0 ? "整个空间已停止 · 深冻 " + r.done + "/" + r.total : "整个空间已停止 · 正在建立深冻清单";
        }
        if (r.isThawing()) return "正在恢复 App 状态并启动空间";
        if (r.isFrozen()) return "已深度冻结 " + r.count + " 个包" + (r.fail > 0 ? " · " + r.fail + " 个失败" : "") + " · Shared已卸载";
        if (r.running) return r.shared ? "真实运行 · Shared真实挂载" : "真实运行 · Shared等待自动挂载";
        return "当前未运行 · 未冻结";
    }

    private void runAction(String command, String success) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult r = runSu(command);
            main.post(() -> {
                Toast.makeText(this, r.ok ? success : "操作失败：" + oneLine(r.output), Toast.LENGTH_LONG).show();
                setBusy(false);
                refresh();
            });
        });
    }

    private CommandResult runSu(String command) {
        StringBuilder out = new StringBuilder();
        Process p = null;
        int code = -1;
        try {
            p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) out.append(line).append('\n');
            if (p.waitFor(180, TimeUnit.SECONDS)) code = p.exitValue();
            else {
                p.destroyForcibly();
                out.append("timeout after 180s");
            }
        } catch (Exception e) {
            out.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
        return new CommandResult(code == 0, out.toString().trim());
    }

    private TextView addStat(LinearLayout parent, String title) {
        LinearLayout box = column();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2), dp(8), dp(2), dp(8));
        box.setBackground(round(Color.rgb(249, 250, 253), 12));
        TextView value = label("-", 19, true, TEXT);
        value.setGravity(Gravity.CENTER);
        box.addView(value);
        TextView name = label(title, 10, false, MUTED);
        name.setGravity(Gravity.CENTER);
        box.addView(name);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(box, lp);
        return value;
    }

    private void setStats(String total, String frozen, String running, String shared) {
        totalValue.setText(total);
        frozenValue.setText(frozen);
        runningValue.setText(running);
        sharedValue.setText(shared);
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private LinearLayout card(int radius, int padding) {
        LinearLayout v = column();
        v.setPadding(dp(padding), dp(padding), dp(padding), dp(padding));
        GradientDrawable g = round(CARD, radius);
        g.setStroke(dp(1), BORDER);
        v.setBackground(g);
        return v;
    }

    private TextView label(String text, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView pill(String text, int bg, int fg) {
        TextView t = label(text, 12, true, fg);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
        t.setBackground(round(bg, 99));
        return t;
    }

    private void stylePill(TextView t, int bg, int fg) {
        t.setTextColor(fg);
        t.setBackground(round(bg, 99));
    }

    private Button action(String text, int bg, int fg) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(fg);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(6), 0, dp(6), 0);
        b.setBackground(round(bg, 13));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private void stroke(Button b) {
        GradientDrawable g = round(Color.WHITE, 13);
        g.setStroke(dp(1), BORDER);
        b.setBackground(g);
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void setBusy(boolean busy) {
        busyIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private String normalizeName(String s) {
        return s == null ? "" : s.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String shellQuote(String s) {
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private String oneLine(String s) {
        if (s == null || s.trim().isEmpty()) return "无输出";
        String v = s.trim().replace('\n', ' ').replace('\r', ' ');
        return v.length() > 240 ? v.substring(0, 240) + "…" : v;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(autoRefresh);
        executor.shutdownNow();
        super.onDestroy();
    }

    private static final class CommandResult {
        final boolean ok;
        final String output;
        CommandResult(boolean ok, String output) { this.ok = ok; this.output = output; }
    }

    private static final class UserRow {
        int id;
        String name;
        String state;
        boolean running;
        boolean shared;
        int count;
        int done;
        int total;
        int fail;
        boolean isFrozen() { return "FROZEN".equals(state); }
        boolean isFreezing() { return "FREEZING".equals(state); }
        boolean isThawing() { return "THAWING".equals(state); }
        boolean processing() { return isFreezing() || isThawing(); }
        boolean frozenLike() { return isFrozen() || isFreezing(); }
    }
}
