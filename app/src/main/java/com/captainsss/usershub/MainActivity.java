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

    private LinearLayout listLayout;
    private TextView rootStatus;
    private TextView summary;
    private TextView totalValue;
    private TextView frozenValue;
    private TextView runningValue;
    private TextView sharedValue;
    private ProgressBar topProgress;
    private boolean refreshInFlight = false;

    private final Runnable autoRefresh = this::requestRootAndRefresh;

    private static final int BG = Color.rgb(246, 248, 252);
    private static final int CARD = Color.WHITE;
    private static final int TEXT = Color.rgb(28, 31, 38);
    private static final int MUTED = Color.rgb(116, 123, 135);
    private static final int BORDER = Color.rgb(229, 233, 240);
    private static final int GREEN = Color.rgb(27, 157, 88);
    private static final int GREEN_BG = Color.rgb(232, 248, 239);
    private static final int RED = Color.rgb(191, 55, 61);
    private static final int RED_BG = Color.rgb(255, 238, 239);
    private static final int BLUE = Color.rgb(55, 105, 180);
    private static final int BLUE_BG = Color.rgb(235, 242, 255);
    private static final int ORANGE = Color.rgb(214, 133, 35);
    private static final int ORANGE_BG = Color.rgb(255, 246, 229);
    private static final int GRAY = Color.rgb(144, 151, 163);
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
        page.setPadding(dp(16), dp(22), dp(16), dp(32));
        scroll.addView(page, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = text("UsersHub  1.4", 12, true, BLUE);
        brand.setLetterSpacing(0.08f);
        page.addView(brand);

        TextView title = text("空间控制", 30, true, TEXT);
        title.setPadding(0, dp(5), 0, 0);
        page.addView(title);

        TextView subtitle = text("整空间秒停 · 自定义空间 · 自动真实 Shared", 13, false, MUTED);
        subtitle.setPadding(0, dp(5), 0, dp(14));
        page.addView(subtitle);

        LinearLayout hero = card(20, dp(18));
        LinearLayout rootLine = new LinearLayout(this);
        rootLine.setOrientation(LinearLayout.HORIZONTAL);
        rootLine.setGravity(Gravity.CENTER_VERTICAL);
        rootStatus = pill("Root 正在连接", ORANGE_BG, ORANGE);
        rootLine.addView(rootStatus);

        topProgress = new ProgressBar(this);
        LinearLayout.LayoutParams topP = new LinearLayout.LayoutParams(dp(22), dp(22));
        topP.leftMargin = dp(10);
        topProgress.setLayoutParams(topP);
        rootLine.addView(topProgress);
        hero.addView(rootLine);

        summary = text("正在读取空间状态…", 13, false, MUTED);
        summary.setPadding(0, dp(12), 0, dp(13));
        hero.addView(summary);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        totalValue = addStat(stats, "空间");
        frozenValue = addStat(stats, "冻结");
        runningValue = addStat(stats, "运行");
        sharedValue = addStat(stats, "Shared");
        hero.addView(stats);
        page.addView(hero);

        LinearLayout primary = new LinearLayout(this);
        primary.setOrientation(LinearLayout.HORIZONTAL);
        primary.setPadding(0, dp(12), 0, dp(5));

        Button freezeAll = actionButton("全部冻结", RED_BG, RED);
        freezeAll.setOnClickListener(v -> runRootAction(
                "usershubctl freeze-all",
                "空间已立即停止，灰图标正在后台完成"));
        primary.addView(freezeAll, weightedButton());

        Button thawAll = actionButton("全部解冻", GREEN_BG, GREEN);
        thawAll.setOnClickListener(v -> runRootAction(
                "usershubctl thaw-all",
                "全部空间解冻完成"));
        primary.addView(thawAll, weightedButton());
        page.addView(primary);

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        secondary.setPadding(0, dp(5), 0, dp(16));

        Button refresh = actionButton("刷新状态", Color.WHITE, TEXT);
        addStroke(refresh, BORDER);
        refresh.setOnClickListener(v -> requestRootAndRefresh());
        secondary.addView(refresh, weightedButton());

        Button create = actionButton("＋ 创建空间", BLUE_BG, BLUE);
        create.setOnClickListener(v -> showCreateDialog());
        secondary.addView(create, weightedButton());
        page.addView(secondary);

        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView listTitle = text("空间列表", 17, true, TEXT);
        listHeader.addView(listTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView auto = text("处理中自动刷新", 11, false, MUTED);
        listHeader.addView(auto);
        page.addView(listHeader);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(9);
        listLayout.setLayoutParams(lp);
        page.addView(listLayout);

        TextView note = text(
                "创建空间会调用 Android 的真实 Full User 创建命令，名称可自定义，ID 由系统自动分配。选择“创建并启动”后，空间真正启动时 Shared 会自动挂载；选择“仅创建”则保持停止，不会为了 Shared 被后台偷偷启动。冻结仍然先秒停整个 User，再后台并行补灰图标。",
                11, false, MUTED);
        note.setPadding(dp(3), dp(14), dp(3), 0);
        page.addView(note);

        setContentView(scroll);
    }

    private void showCreateDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(4), dp(22), 0);

        TextView hint = text("创建真实 Android 次级用户空间，可输入中文、英文或数字名称。", 12, false, MUTED);
        hint.setPadding(0, 0, 0, dp(10));
        box.addView(hint);

        EditText input = new EditText(this);
        input.setHint("例如：工作A / 测试01");
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("创建新空间")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("仅创建", null)
                .setPositiveButton("创建并启动", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = normalizeSpaceName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError("请输入空间名称");
                    return;
                }
                dialog.dismiss();
                createSpace(name, true);
            });
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String name = normalizeSpaceName(input.getText().toString());
                if (name.isEmpty()) {
                    input.setError("请输入空间名称");
                    return;
                }
                dialog.dismiss();
                createSpace(name, false);
            });
        });

        dialog.show();
    }

    private void createSpace(String name, boolean start) {
        String command = "usershubcreate " + (start ? "create-start " : "create ") + shellQuote(name);
        runRootAction(command, start ? "空间已创建并启动" : "空间已创建");
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
        box.setPadding(dp(4), dp(10), dp(4), dp(9));
        box.setBackground(rounded(Color.rgb(248, 249, 252), 13));

        TextView value = text("-", 21, true, TEXT);
        value.setGravity(Gravity.CENTER);
        box.addView(value);
        TextView lab = text(label, 11, false, MUTED);
        lab.setGravity(Gravity.CENTER);
        lab.setPadding(0, dp(2), 0, 0);
        box.addView(lab);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
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
                    summary.setText("请在 Magisk 超级用户中允许 UsersHub，然后点刷新状态。");
                    totalValue.setText("-");
                    frozenValue.setText("-");
                    runningValue.setText("-");
                    sharedValue.setText("-");
                    listLayout.removeAllViews();
                    refreshInFlight = false;
                    setBusy(false);
                });
                return;
            }

            CommandResult helper = runSu(
                    "if command -v usershubctl >/dev/null 2>&1 && command -v usershubcreate >/dev/null 2>&1; then usershubctl status --plain; else echo __MODULE_MISSING__; fi");
            main.post(() -> {
                rootStatus.setText("Root 已连接");
                stylePill(rootStatus, GREEN_BG, GREEN);

                if (helper.output.contains("__MODULE_MISSING__")) {
                    summary.setText("请安装 UsersHub Core v1.4.0 Magisk 模块并重启。");
                    listLayout.removeAllViews();
                } else if (!helper.ok) {
                    summary.setText("状态读取失败：" + oneLine(helper.output));
                    listLayout.removeAllViews();
                } else {
                    renderStatus(helper.output);
                }

                refreshInFlight = false;
                setBusy(false);
            });
        });
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
                r.count = Integer.parseInt(p[5]);
                r.done = p.length > 6 ? safeInt(p[6]) : r.count;
                r.total = p.length > 7 ? safeInt(p[7]) : r.count;
                r.fail = p.length > 8 ? safeInt(p[8]) : 0;
                rows.add(r);
                if (r.isProcessing()) processing = true;
            } catch (Exception ignored) {}
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

        summary.setText(processing
                ? "空间状态已更新 · 后台正在并行处理灰图标，不影响继续操作"
                : "全部操作空闲 · 冻结空间已停止，运行空间可正常使用");

        if (rows.isEmpty()) {
            LinearLayout empty = card(16, dp(16));
            TextView emptyTitle = text("还没有次级空间", 16, true, TEXT);
            empty.addView(emptyTitle);
            TextView emptySub = text("点上方“创建空间”即可创建第一个真实 Android 用户空间。", 12, false, MUTED);
            emptySub.setPadding(0, dp(5), 0, 0);
            empty.addView(emptySub);
            listLayout.addView(empty);
        } else {
            for (UserRow r : rows) listLayout.addView(buildUserCard(r));
        }

        if (processing) main.postDelayed(autoRefresh, 1400);
    }

    private View buildUserCard(UserRow r) {
        LinearLayout card = card(18, dp(14));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(10);
        card.setLayoutParams(cardLp);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView idCircle = text(String.valueOf(r.id), 13, true, Color.WHITE);
        idCircle.setGravity(Gravity.CENTER);
        int circleColor = r.isFreezing() ? ORANGE : r.isThawing() ? BLUE : r.isFrozen() ? GRAY : GREEN;
        idCircle.setBackground(rounded(circleColor, 24));
        top.addView(idCircle, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoLp.leftMargin = dp(12);
        info.setLayoutParams(infoLp);

        String displayName = r.name == null ? "" : r.name.trim();
        if (displayName.isEmpty() || displayName.equals(String.valueOf(r.id))) {
            displayName = "空间 " + r.id;
        }
        TextView name = text(displayName, 17, true, TEXT);
        info.addView(name);

        TextView idLabel = text("ID " + r.id, 10, false, MUTED);
        idLabel.setPadding(0, dp(2), 0, 0);
        info.addView(idLabel);

        TextView status = statusPill(r);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = dp(5);
        info.addView(status, statusLp);
        top.addView(info);

        Button toggle;
        if (r.isProcessing()) {
            toggle = actionButton(r.isFreezing() ? "冻结中" : "解冻中", GRAY_BG, GRAY);
            toggle.setEnabled(false);
            toggle.setAlpha(0.8f);
        } else if (r.isFrozen()) {
            toggle = actionButton("解冻", GREEN_BG, GREEN);
            toggle.setOnClickListener(v -> runRootAction(
                    "usershubctl thaw " + r.id,
                    displayName + " 已解冻"));
        } else {
            toggle = actionButton("冻结", GRAY_BG, Color.rgb(78, 85, 97));
            toggle.setOnClickListener(v -> runRootAction(
                    "usershubctl freeze " + r.id,
                    displayName + " 已立即停止，灰图标后台处理中"));
        }
        top.addView(toggle, new LinearLayout.LayoutParams(dp(86), dp(44)));
        card.addView(top);

        TextView detail = text(detailText(r), 12, false, MUTED);
        detail.setPadding(dp(54), dp(10), 0, 0);
        card.addView(detail);

        if (r.isFreezing() && r.total > 0) {
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(r.total);
            bar.setProgress(Math.min(r.done, r.total));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(5));
            bp.leftMargin = dp(54);
            bp.topMargin = dp(10);
            card.addView(bar, bp);
        }

        return card;
    }

    private TextView statusPill(UserRow r) {
        if (r.isFreezing()) return pill("冻结中", ORANGE_BG, ORANGE);
        if (r.isThawing()) return pill("解冻中", BLUE_BG, BLUE);
        if (r.isFrozen()) return pill("已冻结", GRAY_BG, Color.rgb(91, 98, 110));
        if (r.running) return pill("运行中", GREEN_BG, GREEN);
        return pill("未运行", GRAY_BG, GRAY);
    }

    private String detailText(UserRow r) {
        if (r.isFreezing()) {
            String p = r.total > 0 ? (" · 深冻进度 " + r.done + "/" + r.total) : " · 正在建立冻结清单";
            return "整个空间已先停止" + p + (r.shared ? " · Shared待卸载" : " · Shared已卸载");
        }
        if (r.isThawing()) return "正在恢复 App 状态并启动整个空间…";
        if (r.isFrozen()) {
            String fail = r.fail > 0 ? (" · " + r.fail + " 个普通包失败") : "";
            return "已深度冻结 " + r.count + " 个包" + fail + (r.shared ? " · Shared仍挂载" : " · Shared已卸载");
        }
        if (r.running) return r.shared ? "真实运行中 · Shared真实挂载" : "真实运行中 · Shared等待自动挂载";
        return r.shared ? "当前未运行 · Shared等待后台卸载" : "当前未运行 · 未冻结";
    }

    private void runRootAction(String command, String successMessage) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult result = runSu(command);
            main.post(() -> {
                Toast.makeText(this,
                        result.ok ? successMessage : ("操作失败：" + oneLine(result.output)),
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
            if (process.waitFor(180, TimeUnit.SECONDS)) code = process.exitValue();
            else {
                process.destroyForcibly();
                sb.append("timeout after 180s");
            }
        } catch (Exception e) {
            sb.append(e.getClass().getSimpleName()).append(": ").append(e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
        return new CommandResult(code == 0, sb.toString().trim());
    }

    private void setBusy(boolean busy) {
        topProgress.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private String oneLine(String s) {
        if (s == null || s.trim().isEmpty()) return "无输出";
        String v = s.trim().replace('\n', ' ').replace('\r', ' ');
        return v.length() > 220 ? v.substring(0, 220) + "…" : v;
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
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(textColor);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackground(rounded(bgColor, 13));
        if (android.os.Build.VERSION.SDK_INT >= 21) b.setStateListAnimator(null);
        return b;
    }

    private void addStroke(Button b, int color) {
        GradientDrawable g = rounded(Color.WHITE, 13);
        g.setStroke(dp(1), color);
        b.setBackground(g);
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private TextView pill(String s, int bgColor, int textColor) {
        TextView t = text(s, 12, true, textColor);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(5), dp(10), dp(5));
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(autoRefresh);
        executor.shutdownNow();
        super.onDestroy();
    }

    private static class CommandResult {
        final boolean ok;
        final String output;
        CommandResult(boolean ok, String output) {
            this.ok = ok;
            this.output = output;
        }
    }

    private static class UserRow {
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
        boolean isProcessing() { return isFreezing() || isThawing(); }
    }
}
