package com.captainsss.usershub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable autoRefresh = this::requestRootAndRefresh;

    private LinearLayout listLayout;
    private TextView rootStatus, summary, totalValue, frozenValue, runningValue, sharedValue;
    private ProgressBar topProgress;
    private boolean refreshInFlight = false;

    private static final int BG = Color.rgb(247, 249, 252);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(27, 31, 38);
    private static final int MUTED = Color.rgb(116, 123, 135);
    private static final int BORDER = Color.rgb(229, 233, 240);
    private static final int GREEN = Color.rgb(24, 153, 88);
    private static final int GREEN_BG = Color.rgb(232, 248, 239);
    private static final int RED = Color.rgb(190, 54, 60);
    private static final int RED_BG = Color.rgb(255, 238, 239);
    private static final int BLUE = Color.rgb(47, 103, 190);
    private static final int BLUE_BG = Color.rgb(235, 242, 255);
    private static final int ORANGE = Color.rgb(201, 121, 28);
    private static final int ORANGE_BG = Color.rgb(255, 245, 228);
    private static final int GRAY = Color.rgb(145, 152, 164);
    private static final int GRAY_BG = Color.rgb(240, 242, 246);

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

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(16), dp(14), dp(28));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = text("UsersHub  " + getAppVersion(), 12, true, BLUE);
        brand.setLetterSpacing(0.06f);
        page.addView(brand);

        TextView title = text("空间控制", 28, true, TEXT);
        title.setPadding(0, dp(3), 0, 0);
        page.addView(title);

        TextView subtitle = text("工作空间 · 秒停深冻 · 自动真实 Shared", 12, false, MUTED);
        subtitle.setPadding(0, dp(3), 0, dp(12));
        page.addView(subtitle);

        LinearLayout hero = card(18, dp(14));
        LinearLayout rootLine = new LinearLayout(this);
        rootLine.setOrientation(LinearLayout.HORIZONTAL);
        rootLine.setGravity(Gravity.CENTER_VERTICAL);
        rootStatus = pill("Root 正在连接", ORANGE_BG, ORANGE);
        rootLine.addView(rootStatus);
        topProgress = new ProgressBar(this);
        LinearLayout.LayoutParams topP = new LinearLayout.LayoutParams(dp(19), dp(19));
        topP.leftMargin = dp(8);
        topProgress.setLayoutParams(topP);
        rootLine.addView(topProgress);
        hero.addView(rootLine);

        summary = text("正在读取空间状态…", 12, false, MUTED);
        summary.setPadding(0, dp(9), 0, dp(10));
        hero.addView(summary);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        totalValue = addStat(stats, "空间");
        frozenValue = addStat(stats, "冻结");
        runningValue = addStat(stats, "运行");
        sharedValue = addStat(stats, "Shared");
        hero.addView(stats);
        page.addView(hero);

        LinearLayout primary = buttonRow();
        primary.setPadding(0, dp(10), 0, dp(4));
        Button freezeAll = actionButton("全部冻结", RED_BG, RED);
        freezeAll.setOnClickListener(v -> runRootAction("usershubctl freeze-all", "全部空间已提交冻结"));
        primary.addView(freezeAll, weightedButton());
        Button thawAll = actionButton("全部解冻", GREEN_BG, GREEN);
        thawAll.setOnClickListener(v -> runRootAction("usershubctl thaw-all", "全部空间解冻完成"));
        primary.addView(thawAll, weightedButton());
        page.addView(primary);

        LinearLayout secondary = buttonRow();
        secondary.setPadding(0, dp(4), 0, dp(14));
        Button refresh = actionButton("刷新", Color.WHITE, TEXT);
        addStroke(refresh, BORDER);
        refresh.setOnClickListener(v -> requestRootAndRefresh());
        secondary.addView(refresh, weightedButton());
        Button create = actionButton("＋ 创建工作空间", BLUE_BG, BLUE);
        create.setOnClickListener(v -> showCreateDialog());
        secondary.addView(create, weightedButton());
        page.addView(secondary);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("空间列表", 17, true, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(text("状态自动刷新", 10, false, MUTED));
        page.addView(header);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = dp(8);
        page.addView(listLayout, listLp);

        TextView note = text("删除空间会永久清除该空间的数据。Android 通常在本次开机内暂不复用刚删除的 User ID；如果要让后续创建优先复用 20/21，请删除后先重启再创建。", 10, false, MUTED);
        note.setPadding(dp(2), dp(10), dp(2), 0);
        page.addView(note);

        setContentView(scroll);
    }

    private String getAppVersion() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void showCreateDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(2), dp(20), 0);
        TextView hint = text("创建与现有 10～19 同类型的 Managed Profile，并同步模板空间的第三方 App 安装关系。", 12, false, MUTED);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText input = new EditText(this);
        input.setHint("例如：工作20 / 店铺A");
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("创建工作空间")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("仅创建", null)
                .setPositiveButton("创建并启动", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = normalizeSpaceName(input.getText().toString());
                if (name.isEmpty()) { input.setError("请输入空间名称"); return; }
                dialog.dismiss();
                createSpace(name, true);
            });
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String name = normalizeSpaceName(input.getText().toString());
                if (name.isEmpty()) { input.setError("请输入空间名称"); return; }
                dialog.dismiss();
                createSpace(name, false);
            });
        });
        dialog.show();
    }

    private void createSpace(String name, boolean start) {
        String command = "usershubcreate " + (start ? "create-start " : "create ") + shellQuote(name);
        runRootAction(command, start ? "工作空间已创建并启动" : "工作空间已创建");
    }

    private void showDeleteDialog(UserRow r, String displayName) {
        String extra = "FULL".equals(r.kind)
                ? "\n\n这个是独立 Full User。删除后如果希望下一次创建优先重新使用 ID " + r.id + "，请先重启手机再创建。"
                : "\n\n这是工作空间。删除后其中的应用数据、账号和文件都会永久清除。";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除 " + displayName + "？")
                .setMessage("将永久删除 User " + r.id + " 的全部数据，此操作无法撤销。" + extra)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认删除", (d, which) -> runRootAction(
                        "usershubcreate delete " + r.id,
                        "已删除 " + displayName + "。如需复用该 ID，请先重启再创建。"))
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setTextColor(RED));
        dialog.show();
    }

    private String normalizeSpaceName(String input) {
        if (input == null) return "";
        return input.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private TextView addStat(LinearLayout parent, String label) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(2), dp(8), dp(2), dp(7));
        box.setBackground(rounded(Color.rgb(248, 249, 252), 12));
        TextView value = text("-", 20, true, TEXT);
        value.setGravity(Gravity.CENTER);
        box.addView(value);
        TextView lab = text(label, 10, false, MUTED);
        lab.setGravity(Gravity.CENTER);
        lab.setPadding(0, dp(1), 0, 0);
        box.addView(lab);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), 0, dp(2), 0);
        parent.addView(box, p);
        return value;
    }

    private void requestRootAndRefresh() {
        if (refreshInFlight) return;
        refreshInFlight = true;
        main.removeCallbacks(autoRefresh);
        setBusy(true);
        executor.execute(() -> {
            CommandResult id = runSu("id -u");
            if (!id.ok || !id.output.trim().equals("0")) {
                main.post(() -> {
                    rootStatus.setText("Root 未授权");
                    stylePill(rootStatus, RED_BG, RED);
                    summary.setText("请在 Magisk 超级用户中允许 UsersHub。");
                    clearStats();
                    listLayout.removeAllViews();
                    refreshInFlight = false;
                    setBusy(false);
                });
                return;
            }
            CommandResult helper = runSu("if command -v usershubstatus >/dev/null 2>&1 && command -v usershubcreate >/dev/null 2>&1; then usershubstatus --plain; else echo __MODULE_MISSING__; fi");
            main.post(() -> {
                rootStatus.setText("Root 已连接");
                stylePill(rootStatus, GREEN_BG, GREEN);
                if (helper.output.contains("__MODULE_MISSING__")) {
                    summary.setText("请安装与 APK 同版本的 UsersHub Core Magisk 模块并重启。");
                    clearStats();
                    listLayout.removeAllViews();
                } else if (!helper.ok) {
                    summary.setText("状态读取失败：" + oneLine(helper.output));
                    clearStats();
                    listLayout.removeAllViews();
                } else {
                    renderStatus(helper.output);
                }
                refreshInFlight = false;
                setBusy(false);
            });
        });
    }

    private void clearStats() {
        totalValue.setText("-");
        frozenValue.setText("-");
        runningValue.setText("-");
        sharedValue.setText("-");
    }

    private void renderStatus(String output) {
        listLayout.removeAllViews();
        List<UserRow> rows = new ArrayList<>();
        boolean processing = false;
        for (String line : output.split("\\r?\\n")) {
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 6) continue;
            try {
                UserRow r = new UserRow();
                r.id = Integer.parseInt(p[0]);
                r.name = p[1].isEmpty() ? String.valueOf(r.id) : p[1];
                r.state = p[2];
                r.running = "RUNNING".equals(p[3]);
                r.shared = "SHARED".equals(p[4]);
                r.count = safeInt(p[5]);
                r.done = p.length > 6 ? safeInt(p[6]) : r.count;
                r.total = p.length > 7 ? safeInt(p[7]) : r.count;
                r.fail = p.length > 8 ? safeInt(p[8]) : 0;
                r.kind = p.length > 9 ? p[9] : "OTHER";
                r.flags = p.length > 10 ? p[10] : "";
                rows.add(r);
                if (r.isProcessing()) processing = true;
            } catch (Exception ignored) { }
        }

        int frozen = 0, running = 0, shared = 0;
        for (UserRow r : rows) {
            if (r.isFrozen() || r.isFreezing()) frozen++;
            if (r.running) running++;
            if (r.shared) shared++;
        }
        totalValue.setText(String.valueOf(rows.size()));
        frozenValue.setText(String.valueOf(frozen));
        runningValue.setText(String.valueOf(running));
        sharedValue.setText(String.valueOf(shared));
        summary.setText(processing ? "后台正在处理灰图标，可继续操作" : "状态已同步到 Android 实际用户生命周期");

        for (UserRow r : rows) listLayout.addView(buildUserCard(r));
        if (rows.isEmpty()) {
            LinearLayout empty = card(16, dp(14));
            empty.addView(text("还没有次级空间", 16, true, TEXT));
            listLayout.addView(empty);
        }
        if (processing) main.postDelayed(autoRefresh, 1400);
    }

    private View buildUserCard(UserRow r) {
        LinearLayout card = card(16, dp(12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = dp(8);
        card.setLayoutParams(cp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView idCircle = text(String.valueOf(r.id), 12, true, Color.WHITE);
        idCircle.setGravity(Gravity.CENTER);
        int circleColor = r.isFreezing() ? ORANGE : r.isThawing() ? BLUE : r.isFrozen() ? GRAY : GREEN;
        idCircle.setBackground(rounded(circleColor, 22));
        top.addView(idCircle, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ip.leftMargin = dp(10);
        info.setLayoutParams(ip);

        String raw = r.name == null ? "" : r.name.trim();
        final String displayName = raw.isEmpty() || raw.equals(String.valueOf(r.id)) ? "空间 " + r.id : raw;
        info.addView(text(displayName, 16, true, TEXT));
        String type = "WORK".equals(r.kind) ? "工作空间" : "FULL".equals(r.kind) ? "独立用户" : "其他用户";
        TextView typeView = text("ID " + r.id + " · " + type, 10, false, "FULL".equals(r.kind) ? ORANGE : MUTED);
        typeView.setPadding(0, dp(2), 0, 0);
        info.addView(typeView);
        top.addView(info);

        Button toggle;
        if (r.isProcessing()) {
            toggle = actionButton(r.isFreezing() ? "冻结中" : "解冻中", GRAY_BG, GRAY);
            toggle.setEnabled(false);
        } else if (r.isFrozen()) {
            toggle = actionButton("解冻", GREEN_BG, GREEN);
            toggle.setOnClickListener(v -> runRootAction("usershubctl thaw " + r.id, displayName + " 已解冻"));
        } else {
            toggle = actionButton("冻结", GRAY_BG, Color.rgb(78, 85, 97));
            toggle.setOnClickListener(v -> runRootAction("usershubctl freeze " + r.id, displayName + " 已提交冻结"));
        }
        top.addView(toggle, new LinearLayout.LayoutParams(dp(76), dp(40)));
        card.addView(top);

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.HORIZONTAL);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.leftMargin = dp(50);
        mp.topMargin = dp(5);
        meta.setLayoutParams(mp);
        TextView status = statusPill(r);
        meta.addView(status);
        View spacer = new View(this);
        meta.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        Button delete = actionButton("删除", RED_BG, RED);
        delete.setTextSize(10);
        delete.setOnClickListener(v -> showDeleteDialog(r, displayName));
        meta.addView(delete, new LinearLayout.LayoutParams(dp(56), dp(32)));
        card.addView(meta);

        TextView detail = text(detailText(r), 11, false, MUTED);
        detail.setPadding(dp(50), dp(6), 0, 0);
        card.addView(detail);

        if (r.isFreezing() && r.total > 0) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(r.total);
            bar.setProgress(Math.min(r.done, r.total));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
            bp.leftMargin = dp(50);
            bp.topMargin = dp(6);
            card.addView(bar, bp);
        }
        return card;
    }

    private TextView statusPill(UserRow r) {
        if (r.isFreezing()) return pill("冻结中", ORANGE_BG, ORANGE);
        if (r.isThawing()) return pill("解冻中", BLUE_BG, BLUE);
        if (r.isFrozen()) return pill("已冻结", GRAY_BG, Color.rgb(90, 97, 109));
        if (r.running) return pill("运行中", GREEN_BG, GREEN);
        return pill("未运行", GRAY_BG, GRAY);
    }

    private String detailText(UserRow r) {
        if ("FULL".equals(r.kind)) return "独立 Full User · flags=" + r.flags + " · 可删除后重启以释放 ID";
        if (r.isFreezing()) return "整个空间已停止 · 灰图标 " + r.done + "/" + r.total;
        if (r.isThawing()) return "正在恢复 App 状态并启动工作空间";
        if (r.isFrozen()) return "已深度冻结 " + r.count + " 个包 · Shared已卸载";
        if (r.running) return r.shared ? "真实运行 · Shared真实挂载" : "真实运行 · Shared等待自动挂载";
        return "工作空间未启动 · Shared未挂载";
    }

    private void runRootAction(String command, String successMessage) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult result = runSu(command);
            main.post(() -> {
                if (result.ok) {
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("操作失败")
                            .setMessage(result.output == null || result.output.trim().isEmpty() ? "无系统返回信息" : result.output)
                            .setPositiveButton("知道了", null)
                            .show();
                }
                setBusy(false);
                requestRootAndRefresh();
                main.postDelayed(this::requestRootAndRefresh, 1600);
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
            if (process.waitFor(180, TimeUnit.SECONDS)) code = process.exitValue();
            else { process.destroyForcibly(); sb.append("timeout after 180s"); }
        } catch (Exception e) {
            sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
        return new CommandResult(code == 0, sb.toString().trim());
    }

    private void setBusy(boolean busy) { topProgress.setVisibility(busy ? View.VISIBLE : View.GONE); }
    private int safeInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private String oneLine(String s) {
        if (s == null || s.trim().isEmpty()) return "无输出";
        String v = s.trim().replace('\n', ' ').replace('\r', ' ');
        return v.length() > 200 ? v.substring(0, 200) + "…" : v;
    }

    private LinearLayout card(int radius, int padding) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(padding, padding, padding, padding);
        GradientDrawable bg = rounded(CARD, radius);
        bg.setStroke(dp(1), BORDER);
        v.setBackground(bg);
        return v;
    }

    private Button actionButton(String label, int bgColor, int textColor) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackground(rounded(bgColor, 12));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private void addStroke(Button b, int color) {
        GradientDrawable g = rounded(Color.WHITE, 12);
        g.setStroke(dp(1), color);
        b.setBackground(g);
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        lp.setMargins(dp(2), 0, dp(2), 0);
        return lp;
    }

    private TextView pill(String s, int bgColor, int textColor) {
        TextView t = text(s, 11, true, textColor);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(9), dp(4), dp(9), dp(4));
        t.setBackground(rounded(bgColor, 99));
        return t;
    }

    private void stylePill(TextView t, int bgColor, int textColor) {
        t.setTextColor(textColor);
        t.setBackground(rounded(bgColor, 99));
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

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(autoRefresh);
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
        String name, state, kind, flags;
        boolean running, shared;
        int count, done, total, fail;
        boolean isFrozen() { return "FROZEN".equals(state); }
        boolean isFreezing() { return "FREEZING".equals(state); }
        boolean isThawing() { return "THAWING".equals(state); }
        boolean isProcessing() { return isFreezing() || isThawing(); }
    }
}
