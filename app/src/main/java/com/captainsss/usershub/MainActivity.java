package com.captainsss.usershub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import com.captainsss.usershub.identity.IdentityActivity;
import com.captainsss.usershub.ui.UiKit;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final int BG = UiKit.BACKGROUND;
    private static final int CARD = UiKit.SURFACE;
    private static final int TEXT = UiKit.TEXT;
    private static final int MUTED = UiKit.MUTED;
    private static final int BORDER = UiKit.BORDER;
    private static final int GREEN = UiKit.SUCCESS;
    private static final int GREEN_BG = UiKit.SUCCESS_CONTAINER;
    private static final int RED = UiKit.DANGER;
    private static final int RED_BG = UiKit.DANGER_CONTAINER;
    private static final int BLUE = UiKit.PRIMARY;
    private static final int BLUE_BG = UiKit.PRIMARY_CONTAINER;
    private static final int ORANGE = UiKit.WARNING;
    private static final int ORANGE_BG = UiKit.WARNING_CONTAINER;
    private static final int GRAY = UiKit.NEUTRAL;
    private static final int GRAY_BG = UiKit.NEUTRAL_CONTAINER;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiKit.configureWindow(this);
        buildUi();
        requestRootAndRefresh();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(16), dp(16), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView brand = text("USERSHUB  ·  " + getAppVersion(), 11, true, BLUE);
        brand.setLetterSpacing(0.06f);
        page.addView(brand);

        TextView title = text("工作空间", 28, true, TEXT);
        title.setPadding(0, dp(5), 0, 0);
        page.addView(title);

        TextView subtitle = text("隔离应用、快速冻结，并自动连接共享文件", 12, false, MUTED);
        subtitle.setPadding(0, dp(5), 0, dp(14));
        page.addView(subtitle);

        LinearLayout hero = card(20, dp(16));
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
        summary.setPadding(0, dp(10), 0, dp(12));
        hero.addView(summary);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        totalValue = addStat(stats, "空间");
        frozenValue = addStat(stats, "冻结");
        runningValue = addStat(stats, "运行");
        sharedValue = addStat(stats, "共享文件");
        hero.addView(stats);
        page.addView(hero);

        Button create = actionButton("创建工作空间", BLUE, Color.WHITE);
        setActionIcon(create, R.drawable.ic_action_add, Color.WHITE);
        create.setOnClickListener(v -> showCreateDialog());
        LinearLayout.LayoutParams createLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        createLp.topMargin = dp(12);
        page.addView(create, createLp);

        LinearLayout secondary = buttonRow();
        secondary.setPadding(0, dp(8), 0, dp(16));
        Button refresh = actionButton("刷新", Color.WHITE, TEXT);
        addStroke(refresh, BORDER);
        setActionIcon(refresh, R.drawable.ic_action_refresh, TEXT);
        refresh.setOnClickListener(v -> requestRootAndRefresh());
        secondary.addView(refresh, weightedButton());
        Button batch = actionButton("批量", Color.WHITE, TEXT);
        addStroke(batch, BORDER);
        batch.setOnClickListener(v -> showBatchMenu());
        secondary.addView(batch, weightedButton());
        Button identity = actionButton("设备配置", BLUE_BG, BLUE);
        setActionIcon(identity, R.drawable.ic_action_identity, BLUE);
        identity.setOnClickListener(v -> startActivity(new Intent(this, IdentityActivity.class)));
        secondary.addView(identity, weightedButton());
        page.addView(secondary);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("我的空间", 18, true, TEXT), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(text("自动更新状态", 10, false, MUTED));
        page.addView(header);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listLp.topMargin = dp(8);
        page.addView(listLayout, listLp);

        LinearLayout guide = card(14, dp(14));
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        guideLp.topMargin = dp(4);
        guide.setLayoutParams(guideLp);

        TextView guideTitle = text("关于空间编号", 13, true, TEXT);
        guide.addView(guideTitle);

        TextView guideScope = pill("删除操作不会影响其他空间", BLUE_BG, BLUE);
        LinearLayout.LayoutParams scopeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scopeLp.topMargin = dp(7);
        guide.addView(guideScope, scopeLp);

        TextView guideBody = text(
                "Android 会按当前可用编号分配空间 ID。删除空间后，系统通常要到重启以后才会再次使用空缺编号；最终编号始终以 Android 实际分配为准。",
                11, false, MUTED);
        guideBody.setLineSpacing(dp(3), 1f);
        guideBody.setPadding(0, dp(9), 0, 0);
        guide.addView(guideBody);
        page.addView(guide);

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
        TextView hint = text("名称留空时，会在系统分配真实 User ID 后自动使用该数字命名，例如 ID 21 就命名为 21。只保留 Android/MIUI 必需的系统组件，不复制第三方应用。", 12, false, MUTED);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText input = new EditText(this);
        input.setHint("可选备注：店铺A（留空则使用实际 ID）");
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
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false);
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEUTRAL), BLUE, false);
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), BLUE, true);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = normalizeSpaceName(input.getText().toString());
                dialog.dismiss();
                createSpace(name, true);
            });
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(v -> {
                String name = normalizeSpaceName(input.getText().toString());
                dialog.dismiss();
                createSpace(name, false);
            });
        });
        dialog.show();
    }

    private void createSpace(String name, boolean start) {
        boolean autoName = name.isEmpty();
        String command = autoName
                ? "usershubcreate " + (start ? "create-auto-start" : "create-auto")
                : "usershubcreate " + (start ? "create-start " : "create ") + shellQuote(name);
        String success = autoName
                ? (start ? "工作空间已创建并启动，名称与实际 User ID 一致" : "工作空间已创建，名称与实际 User ID 一致")
                : (start ? "工作空间已创建并启动" : "工作空间已创建");
        runRootAction(command, success);
    }

    private void showRenameDialog(UserRow r, String displayName) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(2), dp(20), 0);
        TextView hint = text("只修改 UsersHub 中显示的空间备注。真实 User ID " + r.id + " 保持不变，不调用系统隐藏改名接口，也不会影响应用数据。", 12, false, MUTED);
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);

        EditText input = new EditText(this);
        String current = r.name == null ? "" : r.name.trim();
        if (!current.equals(String.valueOf(r.id))) input.setText(current);
        input.setHint("例如：店铺A / 测试环境");
        input.setSingleLine(true);
        input.setTextSize(16);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        input.setSelection(input.getText().length());
        box.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("修改 " + displayName + " 的备注")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存备注", null)
                .create();
        dialog.setOnShowListener(d -> {
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false);
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), BLUE, true);
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String name = normalizeSpaceName(input.getText().toString());
                if (name.isEmpty()) { input.setError("请输入备注名称"); return; }
                dialog.dismiss();
                runRootAction("usershubcreate rename " + r.id + " " + shellQuote(name),
                        "备注已更新，User ID " + r.id + " 未改变");
            });
        });
        dialog.show();
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
        dialog.setOnShowListener(d -> {
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false);
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), RED, true);
        });
        dialog.show();
    }

    private void showAppManager(UserRow r, String displayName) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), dp(4));

        TextView intro = text("所有操作都绑定当前空间的 User ID，不会误开其他空间的同名应用。", 12, false, MUTED);
        intro.setPadding(0, 0, 0, dp(14));
        content.addView(intro);

        Button launch = actionButton("打开应用", GREEN, Color.WHITE);
        setActionIcon(launch, R.drawable.ic_action_thaw, Color.WHITE);
        content.addView(launch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView launchHint = text("按当前空间 ID 精确打开，未启动时会自动启动空间", 11, false, MUTED);
        launchHint.setPadding(dp(4), dp(5), dp(4), dp(14));
        content.addView(launchHint);

        Button permissions = actionButton("管理权限", BLUE_BG, BLUE);
        addStroke(permissions, Color.rgb(207, 220, 245));
        setActionIcon(permissions, R.drawable.ic_action_apps, BLUE);
        content.addView(permissions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView permissionsHint = text("准确管理当前空间内第三方应用的 MIUI 权限", 11, false, MUTED);
        permissionsHint.setPadding(dp(4), dp(5), dp(4), dp(14));
        content.addView(permissionsHint);

        Button install = actionButton("安装应用", BLUE, Color.WHITE);
        setActionIcon(install, R.drawable.ic_action_add, Color.WHITE);
        content.addView(install, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView installHint = text("从主用户或其他空间选择，可一次安装多个", 11, false, MUTED);
        installHint.setPadding(dp(4), dp(5), dp(4), dp(14));
        content.addView(installHint);

        Button remove = actionButton("移除应用", Color.WHITE, RED);
        addStroke(remove, Color.rgb(244, 205, 208));
        setActionIcon(remove, R.drawable.ic_action_delete, RED);
        content.addView(remove, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        TextView removeHint = text("只清除这个空间内的数据，其他空间不受影响", 11, false, MUTED);
        removeHint.setPadding(dp(4), dp(5), dp(4), 0);
        content.addView(removeHint);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(displayName + " · 应用管理")
                .setView(content)
                .setNegativeButton("关闭", null)
                .create();
        launch.setOnClickListener(v -> {
            dialog.dismiss();
            loadLaunchPicker(r, displayName);
        });
        permissions.setOnClickListener(v -> {
            dialog.dismiss();
            loadPermissionPicker(r, displayName);
        });
        install.setOnClickListener(v -> {
            dialog.dismiss();
            loadAppPicker(r, displayName, true);
        });
        remove.setOnClickListener(v -> {
            dialog.dismiss();
            loadAppPicker(r, displayName, false);
        });
        dialog.setOnShowListener(d -> styleDialogButton(
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false));
        dialog.show();
    }

    private void loadPermissionPicker(UserRow r, String displayName) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult result = runSu("usershubapps list " + r.id);
            List<AppRow> apps = result.ok ? parseAppRows(result.output, false) : new ArrayList<>();
            main.post(() -> {
                setBusy(false);
                if (!result.ok) {
                    showError("读取可管理权限应用失败", result.output);
                    return;
                }
                showPermissionPicker(r, displayName, apps);
            });
        });
    }

    private void showPermissionPicker(UserRow r, String displayName, List<AppRow> apps) {
        if (apps.isEmpty()) {
            AlertDialog empty = new AlertDialog.Builder(this)
                    .setTitle("没有可管理的第三方应用")
                    .setMessage(displayName + " 中还没有安装第三方应用。")
                    .setPositiveButton("知道了", null)
                    .create();
            empty.setOnShowListener(d -> styleDialogButton(
                    empty.getButton(DialogInterface.BUTTON_POSITIVE), BLUE, true));
            empty.show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), 0, dp(12), 0);
        TextView hint = text("选择应用 · 权限修改只作用于 User " + r.id, 11, false, MUTED);
        hint.setPadding(dp(6), 0, dp(6), dp(8));
        content.addView(hint);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(BORDER));
        list.setDividerHeight(dp(1));
        int visibleRows = Math.min(apps.size(), 7);
        int rowsHeight = dp(visibleRows * 72) + dp(Math.max(0, visibleRows - 1));
        int availableHeight = getResources().getDisplayMetrics().heightPixels - dp(250);
        int listHeight = Math.max(dp(72), Math.min(rowsHeight, Math.min(dp(520), availableHeight)));
        content.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight));
        list.setAdapter(new AppPickerAdapter(apps, null));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(displayName + " · 管理权限")
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppRow app = apps.get(position);
            dialog.dismiss();
            runRootAction("usershubapps permissions " + r.id + " " + shellQuote(app.packageName),
                    "已打开 " + displayName + " 中 " + app.label + " 的权限页");
        });
        dialog.setOnShowListener(d -> styleDialogButton(
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false));
        dialog.show();
    }

    private void loadLaunchPicker(UserRow r, String displayName) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult result = runSu("usershubapps launchable " + r.id);
            List<AppRow> apps = result.ok ? parseLaunchRows(result.output) : new ArrayList<>();
            main.post(() -> {
                setBusy(false);
                if (!result.ok) {
                    showError("读取可打开应用失败", result.output);
                    return;
                }
                showLaunchPicker(r, displayName, apps);
            });
        });
    }

    private List<AppRow> parseLaunchRows(String output) {
        List<AppRow> rows = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 3 || !"LAUNCH".equals(p[0])) continue;
            boolean duplicate = false;
            for (AppRow existing : rows) {
                if (existing.packageName.equals(p[1])) { duplicate = true; break; }
            }
            if (duplicate) continue;
            AppRow row = new AppRow();
            row.packageName = p[1];
            row.componentName = p[2];
            resolveAppPresentation(row);
            rows.add(row);
        }
        Collections.sort(rows, (a, b) -> {
            int byLabel = a.label.compareToIgnoreCase(b.label);
            return byLabel != 0 ? byLabel : a.packageName.compareToIgnoreCase(b.packageName);
        });
        return rows;
    }

    private void showLaunchPicker(UserRow r, String displayName, List<AppRow> apps) {
        if (apps.isEmpty()) {
            AlertDialog empty = new AlertDialog.Builder(this)
                    .setTitle("没有可打开的应用")
                    .setMessage(displayName + " 中没有可用的桌面应用入口。")
                    .setPositiveButton("知道了", null)
                    .create();
            empty.setOnShowListener(d -> styleDialogButton(
                    empty.getButton(DialogInterface.BUTTON_POSITIVE), BLUE, true));
            empty.show();
            return;
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), 0, dp(12), 0);
        TextView hint = text("共 " + apps.size() + " 个 · 将准确打开 User " + r.id + " 中的应用", 11, false, MUTED);
        hint.setPadding(dp(6), 0, dp(6), dp(8));
        content.addView(hint);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(BORDER));
        list.setDividerHeight(dp(1));
        int visibleRows = Math.min(apps.size(), 7);
        int availableHeight = getResources().getDisplayMetrics().heightPixels - dp(250);
        int rowsHeight = dp(visibleRows * 72) + dp(Math.max(0, visibleRows - 1));
        int listHeight = Math.max(dp(72), Math.min(rowsHeight, Math.min(dp(520), availableHeight)));
        content.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight));
        list.setAdapter(new AppPickerAdapter(apps, null));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("打开 " + displayName + " 的应用")
                .setView(content)
                .setNegativeButton("取消", null)
                .create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            AppRow app = apps.get(position);
            dialog.dismiss();
            runRootAction("usershubapps launch " + r.id + " "
                            + shellQuote(app.packageName) + " " + shellQuote(app.componentName),
                    "已在 " + displayName + " 打开 " + app.label);
        });
        dialog.setOnShowListener(d -> styleDialogButton(
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false));
        dialog.show();
    }

    private void loadAppPicker(UserRow r, String displayName, boolean installing) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult result = runSu("usershubapps list " + r.id);
            List<AppRow> apps = result.ok ? parseAppRows(result.output, installing) : new ArrayList<>();
            main.post(() -> {
                setBusy(false);
                if (!result.ok) {
                    showError("读取应用失败", result.output);
                    return;
                }
                showAppPicker(r, displayName, installing, apps);
            });
        });
    }

    private List<AppRow> parseAppRows(String output, boolean installing) {
        List<AppRow> rows = new ArrayList<>();
        for (String line : output.split("\\r?\\n")) {
            String[] p = line.split("\\|", -1);
            if (p.length < 4 || !"APP".equals(p[0])) continue;
            boolean installed = "1".equals(p[1]);
            if ((installing && installed) || (!installing && !installed)) continue;
            AppRow row = new AppRow();
            row.installed = installed;
            row.enabled = "1".equals(p[2]);
            row.packageName = p[3];
            resolveAppPresentation(row);
            rows.add(row);
        }
        Collections.sort(rows, (a, b) -> {
            int byLabel = a.label.compareToIgnoreCase(b.label);
            return byLabel != 0 ? byLabel : a.packageName.compareToIgnoreCase(b.packageName);
        });
        return rows;
    }

    private void resolveAppPresentation(AppRow row) {
        row.label = row.packageName;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(row.packageName, PackageManager.MATCH_UNINSTALLED_PACKAGES);
            CharSequence label = pm.getApplicationLabel(info);
            if (label != null && !label.toString().trim().isEmpty()) row.label = label.toString().trim();
            row.icon = pm.getApplicationIcon(info);
        } catch (Exception ignored) { }
        if (row.icon == null) row.icon = getDrawable(android.R.drawable.sym_def_app_icon);
    }

    private void showAppPicker(UserRow r, String displayName, boolean installing, List<AppRow> apps) {
        if (apps.isEmpty()) {
            AlertDialog empty = new AlertDialog.Builder(this)
                    .setTitle(installing ? "没有可安装的应用" : "没有可移除的应用")
                    .setMessage(installing
                            ? "电脑或其他 Android 空间里暂时没有更多第三方应用可复用。"
                            : displayName + " 中没有可安全移除的第三方应用。")
                    .setPositiveButton("知道了", null)
                    .create();
            empty.setOnShowListener(d -> styleDialogButton(
                    empty.getButton(DialogInterface.BUTTON_POSITIVE), BLUE, true));
            empty.show();
            return;
        }

        boolean[] selected = new boolean[apps.size()];
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), 0, dp(12), 0);

        TextView counter = text((installing ? "可安装 " : "已安装 ") + apps.size() + " 个 · 已选 0", 11, false, MUTED);
        counter.setPadding(dp(6), 0, dp(6), dp(8));
        content.addView(counter);

        ListView list = new ListView(this);
        list.setDivider(new ColorDrawable(BORDER));
        list.setDividerHeight(dp(1));
        int visibleRows = Math.min(apps.size(), 7);
        list.setVerticalScrollBarEnabled(apps.size() > visibleRows);
        list.setScrollbarFadingEnabled(false);
        int availableHeight = getResources().getDisplayMetrics().heightPixels - dp(280);
        int rowsHeight = dp(visibleRows * 72) + dp(Math.max(0, visibleRows - 1));
        int listHeight = Math.max(dp(72), Math.min(rowsHeight, Math.min(dp(520), availableHeight)));
        content.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, listHeight));

        AppPickerAdapter adapter = new AppPickerAdapter(apps, selected);
        list.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((installing ? "安装应用到 " : "从空间移除应用 · ") + displayName)
                .setView(content)
                .setNegativeButton("取消", null)
                .setNeutralButton("全选", null)
                .setPositiveButton(installing ? "安装所选" : "移除所选", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            Button neutral = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false);
            styleDialogButton(neutral, BLUE, false);
            styleDialogButton(positive, installing ? BLUE : RED, true);

            Runnable refreshSelection = () -> {
                int count = 0;
                for (boolean value : selected) if (value) count++;
                counter.setText((installing ? "可安装 " : "已安装 ") + apps.size() + " 个 · 已选 " + count);
                positive.setText(count == 0
                        ? (installing ? "安装所选" : "移除所选")
                        : (installing ? "安装 (" : "移除 (") + count + ")");
                positive.setEnabled(count > 0);
                positive.setAlpha(count > 0 ? 1f : 0.45f);
                neutral.setText(count == selected.length ? "清空" : "全选");
            };
            adapter.setSelectionChanged(refreshSelection);
            refreshSelection.run();

            positive.setOnClickListener(v -> {
                List<AppRow> picked = new ArrayList<>();
                for (int i = 0; i < apps.size(); i++) if (selected[i]) picked.add(apps.get(i));
                if (picked.isEmpty()) return;
                dialog.dismiss();
                if (installing) runAppAction(r, displayName, true, picked);
                else confirmRemoveApps(r, displayName, picked);
            });
            neutral.setOnClickListener(v -> {
                boolean allSelected = true;
                for (boolean value : selected) if (!value) { allSelected = false; break; }
                for (int i = 0; i < selected.length; i++) selected[i] = !allSelected;
                adapter.notifyDataSetChanged();
                refreshSelection.run();
            });
        });
        dialog.show();
    }

    private void confirmRemoveApps(UserRow r, String displayName, List<AppRow> apps) {
        StringBuilder names = new StringBuilder();
        int shown = Math.min(apps.size(), 8);
        for (int i = 0; i < shown; i++) names.append("\n• ").append(apps.get(i).label);
        if (apps.size() > shown) names.append("\n• 以及另外 ").append(apps.size() - shown).append(" 个应用");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("确认从 " + displayName + " 移除？")
                .setMessage("将永久清除以下应用在 User " + r.id + " 内的账号、设置和文件：" + names
                        + "\n\n主用户及其他空间中的同名应用不会受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认移除", (d, which) -> runAppAction(r, displayName, false, apps))
                .create();
        dialog.setOnShowListener(d -> {
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false);
            styleDialogButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), RED, true);
        });
        dialog.show();
    }

    private void runAppAction(UserRow r, String displayName, boolean installing, List<AppRow> apps) {
        StringBuilder command = new StringBuilder("usershubapps ")
                .append(installing ? "install " : "remove ")
                .append(r.id);
        for (AppRow app : apps) command.append(' ').append(shellQuote(app.packageName));
        String success = installing
                ? "已向 " + displayName + " 安装 " + apps.size() + " 个应用"
                : "已从 " + displayName + " 移除 " + apps.size() + " 个应用";
        runRootAction(command.toString(), success);
    }

    private void showError(String title, String output) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(output == null || output.trim().isEmpty() ? "无系统返回信息" : output)
                .setPositiveButton("知道了", null)
                .create();
        dialog.setOnShowListener(d -> styleDialogButton(
                dialog.getButton(DialogInterface.BUTTON_POSITIVE), BLUE, true));
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
                    summary.setText("请在 Magisk / ReSukiSU / KernelSU 超级用户中允许 UsersHub。");
                    clearStats();
                    listLayout.removeAllViews();
                    refreshInFlight = false;
                    setBusy(false);
                });
                return;
            }
            CommandResult helper = runSu("if command -v usershubstatus >/dev/null 2>&1 && command -v usershubcreate >/dev/null 2>&1 && command -v usershubapps >/dev/null 2>&1; then usershubstatus --plain; else echo __MODULE_MISSING__; fi");
            main.post(() -> {
                rootStatus.setText("Root 已连接");
                stylePill(rootStatus, GREEN_BG, GREEN);
                if (helper.output.contains("__MODULE_MISSING__")) {
                    summary.setText("请检查 UsersHub Core 是否已安装、启用，且版本与 APK 对应；安装后重启。");
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
        TextView nameView = text(displayName, 16, true, TEXT);
        nameView.setSingleLine(true);
        nameView.setEllipsize(TextUtils.TruncateAt.END);
        info.addView(nameView);
        String type = "WORK".equals(r.kind) ? "工作空间" : "FULL".equals(r.kind) ? "独立用户" : "其他用户";
        TextView typeView = text("ID " + r.id + " · " + type, 10, false, "FULL".equals(r.kind) ? ORANGE : MUTED);
        typeView.setPadding(0, dp(2), 0, 0);
        info.addView(typeView);
        top.addView(info);

        LinearLayout rightControls = new LinearLayout(this);
        rightControls.setOrientation(LinearLayout.HORIZONTAL);
        rightControls.setGravity(Gravity.CENTER_VERTICAL);
        TextView status = statusPill(r);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32));
        rightControls.addView(status, statusP);
        Button manage = actionButton("管理", BLUE_BG, BLUE);
        manage.setTextSize(11);
        manage.setContentDescription("管理 " + displayName);
        manage.setOnClickListener(v -> showSpaceMenu(r, displayName));
        LinearLayout.LayoutParams manageP = new LinearLayout.LayoutParams(dp(60), dp(32));
        manageP.leftMargin = dp(6);
        rightControls.addView(manage, manageP);
        top.addView(rightControls);
        card.addView(top);

        TextView detail = text(detailText(r), 11, false, MUTED);
        detail.setPadding(dp(50), dp(6), 0, 0);
        card.addView(detail);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, dp(11), 0, 0);

        Button toggle;
        if (r.isProcessing()) {
            toggle = actionButton(r.isFreezing() ? "冻结中" : "解冻中", GRAY_BG, GRAY);
            setActionIcon(toggle, r.isFreezing() ? R.drawable.ic_action_freeze : R.drawable.ic_action_thaw, GRAY);
            toggle.setEnabled(false);
        } else if (r.isFrozen()) {
            toggle = actionButton("解冻", GREEN, Color.WHITE);
            setActionIcon(toggle, R.drawable.ic_action_thaw, Color.WHITE);
            toggle.setOnClickListener(v -> runRootAction("usershubctl thaw " + r.id, displayName + " 已解冻"));
        } else if (!r.running) {
            toggle = actionButton("启动", GREEN, Color.WHITE);
            setActionIcon(toggle, R.drawable.ic_action_thaw, Color.WHITE);
            toggle.setOnClickListener(v -> runRootAction("usershubctl start " + r.id, displayName + " 已启动，共享文件正在挂载"));
        } else {
            toggle = actionButton("冻结", Color.WHITE, Color.rgb(78, 85, 97));
            addStroke(toggle, BORDER);
            setActionIcon(toggle, R.drawable.ic_action_freeze, Color.rgb(78, 85, 97));
            toggle.setOnClickListener(v -> runRootAction("usershubctl freeze " + r.id, displayName + " 已提交冻结"));
        }
        toggle.setTextSize(11);
        actions.addView(toggle, actionButtonLayout(1.15f));

        Button apps = actionButton("应用", BLUE_BG, BLUE);
        apps.setTextSize(11);
        setActionIcon(apps, R.drawable.ic_action_apps, BLUE);
        apps.setOnClickListener(v -> showAppManager(r, displayName));
        actions.addView(apps, actionButtonLayout(1f));

        Button identityAction = actionButton("设备信息", BLUE_BG, BLUE);
        identityAction.setTextSize(11);
        setActionIcon(identityAction, R.drawable.ic_action_identity, BLUE);
        identityAction.setOnClickListener(v -> startActivity(new Intent(this, IdentityActivity.class).putExtra("userId", r.id)));
        actions.addView(identityAction, actionButtonLayout(1f));
        card.addView(actions);

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
        return pill("未启动", GRAY_BG, GRAY);
    }

    private void showSpaceMenu(UserRow row, String displayName) {
        String[] items = {"修改显示名称", "删除工作空间"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(displayName)
                .setItems(items, (d, which) -> {
                    if (which == 0) showRenameDialog(row, displayName);
                    else showDeleteDialog(row, displayName);
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> styleDialogButton(
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false));
        dialog.show();
    }

    private void showBatchMenu() {
        String[] items = {"全部冻结 · 暂停所有工作空间", "全部解冻 · 恢复所有工作空间"};
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("批量操作")
                .setItems(items, (d, which) -> {
                    if (which == 0) runRootAction("usershubctl freeze-all", "全部空间已提交冻结");
                    else runRootAction("usershubctl thaw-all", "全部空间解冻完成");
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(d -> styleDialogButton(
                dialog.getButton(DialogInterface.BUTTON_NEGATIVE), MUTED, false));
        dialog.show();
    }

    private String detailText(UserRow r) {
        if ("FULL".equals(r.kind)) return "独立用户空间 · 删除后重启可释放编号";
        if (r.isFreezing()) return "正在暂停空间内应用 · " + r.done + "/" + r.total;
        if (r.isThawing()) return "正在恢复应用并启动工作空间";
        if (r.isFrozen()) return "已暂停 " + r.count + " 个应用 · 共享文件未连接";
        if (r.running) return r.shared ? "空间正在运行 · 共享文件已连接" : "空间正在运行 · 共享文件连接中";
        return "空间尚未启动 · 启动后自动连接共享文件";
    }

    private void runRootAction(String command, String successMessage) {
        setBusy(true);
        executor.execute(() -> {
            CommandResult result = runSu(command);
            main.post(() -> {
                if (result.ok) {
                    Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show();
                } else {
                    showError("操作失败", result.output);
                }
                setBusy(false);
                requestRootAndRefresh();
                main.postDelayed(this::requestRootAndRefresh, 1600);
            });
        });
    }

    private CommandResult runSu(String command) {
        RootCommand.Result result = RootCommand.run(command);
        return new CommandResult(result.ok, result.output);
    }

    private void setBusy(boolean busy) { topProgress.setVisibility(busy ? View.VISIBLE : View.GONE); }
    private int safeInt(String s) { try { return Integer.parseInt(s); } catch (Exception e) { return 0; } }
    private String oneLine(String s) {
        if (s == null || s.trim().isEmpty()) return "无输出";
        String v = s.trim().replace('\n', ' ').replace('\r', ' ');
        return v.length() > 200 ? v.substring(0, 200) + "…" : v;
    }

    private LinearLayout card(int radius, int padding) {
        return UiKit.card(this, radius, Math.round(padding / getResources().getDisplayMetrics().density));
    }

    private Button actionButton(String label, int bgColor, int textColor) {
        return UiKit.button(this, label, bgColor, textColor, Color.TRANSPARENT);
    }

    private void setActionIcon(Button button, int drawableRes, int tintColor) {
        Drawable icon = getDrawable(drawableRes).mutate();
        icon.setTint(tintColor);
        icon.setBounds(0, 0, dp(17), dp(17));
        button.setCompoundDrawables(icon, null, null, null);
        button.setCompoundDrawablePadding(dp(6));
    }

    private ImageButton iconButton(int drawableRes, int tintColor, String description) {
        ImageButton button = new ImageButton(this);
        Drawable icon = getDrawable(drawableRes).mutate();
        icon.setTint(tintColor);
        button.setImageDrawable(icon);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setContentDescription(description);
        button.setPadding(dp(7), dp(7), dp(7), dp(7));
        button.setBackground(interactiveBackground(BLUE_BG, Color.TRANSPARENT, 99));
        button.setStateListAnimator(null);
        return button;
    }

    private void addStroke(Button b, int color) {
        b.setBackground(interactiveBackground(Color.WHITE, color, 13));
    }

    private Drawable interactiveBackground(int fillColor, int strokeColor, int radiusDp) {
        return UiKit.interactive(this, fillColor, strokeColor, radiusDp);
    }

    private void styleDialogButton(Button button, int color, boolean filled) {
        if (button == null) return;
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextSize(12);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setTextColor(filled ? Color.WHITE : color);
        button.setBackground(interactiveBackground(
                filled ? color : Color.TRANSPARENT,
                filled ? Color.TRANSPARENT : Color.rgb(224, 228, 235),
                11));
        ViewGroup.LayoutParams raw = button.getLayoutParams();
        if (raw instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
            lp.height = dp(42);
            lp.leftMargin = dp(4);
            lp.rightMargin = dp(4);
            button.setLayoutParams(lp);
        } else if (raw != null) {
            raw.height = dp(42);
            button.setLayoutParams(raw);
        }
    }

    private LinearLayout.LayoutParams weightedButton() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    private LinearLayout.LayoutParams actionButtonLayout(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), weight);
        lp.setMargins(dp(3), 0, dp(3), 0);
        return lp;
    }

    private TextView pill(String s, int bgColor, int textColor) {
        return UiKit.pill(this, s, bgColor, textColor);
    }

    private void stylePill(TextView t, int bgColor, int textColor) {
        t.setTextColor(textColor);
        t.setBackground(rounded(bgColor, 99));
    }

    private TextView text(String s, int sp, boolean bold, int color) {
        return UiKit.text(this, s, sp, bold, color);
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        return UiKit.background(this, color, radiusDp, Color.TRANSPARENT);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(autoRefresh);
        executor.shutdownNow();
        super.onDestroy();
    }

    private class AppPickerAdapter extends BaseAdapter {
        private final List<AppRow> apps;
        private final boolean[] selected;
        private Runnable selectionChanged;

        AppPickerAdapter(List<AppRow> apps, boolean[] selected) {
            this.apps = apps;
            this.selected = selected;
        }

        void setSelectionChanged(Runnable selectionChanged) {
            this.selectionChanged = selectionChanged;
        }

        @Override public int getCount() { return apps.size(); }
        @Override public AppRow getItem(int position) { return apps.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppRow app = apps.get(position);
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(72));
            row.setPadding(dp(8), dp(8), dp(4), dp(8));
            row.setBackground(interactiveBackground(Color.TRANSPARENT, Color.TRANSPARENT, 12));

            ImageView icon = new ImageView(MainActivity.this);
            icon.setImageDrawable(app.icon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout labels = new LinearLayout(MainActivity.this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams labelsP = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelsP.leftMargin = dp(12);
            labelsP.rightMargin = dp(8);

            TextView name = text(app.label, 14, true, TEXT);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            labels.addView(name);

            TextView packageName = text(app.packageName, 10, false, MUTED);
            packageName.setTypeface(Typeface.MONOSPACE);
            packageName.setSingleLine(true);
            packageName.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            packageName.setPadding(0, dp(2), 0, 0);
            labels.addView(packageName);
            row.addView(labels, labelsP);

            if (selected == null) {
                TextView open = text("打开  ›", 12, true, BLUE);
                open.setGravity(Gravity.CENTER);
                row.addView(open, new LinearLayout.LayoutParams(dp(58), dp(48)));
            } else {
                CheckBox check = new CheckBox(MainActivity.this);
                check.setChecked(selected[position]);
                check.setClickable(false);
                check.setFocusable(false);
                check.setButtonTintList(new ColorStateList(
                        new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                        new int[]{BLUE, Color.rgb(154, 161, 173)}));
                row.addView(check, new LinearLayout.LayoutParams(dp(48), dp(48)));

                row.setOnClickListener(v -> {
                    selected[position] = !selected[position];
                    check.setChecked(selected[position]);
                    if (selectionChanged != null) selectionChanged.run();
                });
            }
            return row;
        }
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

    private static class AppRow {
        String packageName, componentName, label;
        Drawable icon;
        boolean installed, enabled;
    }
}
