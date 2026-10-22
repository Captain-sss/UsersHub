package com.captainsss.usershub.identity;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import com.captainsss.usershub.RootCommand;
import com.captainsss.usershub.ui.UiKit;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Native management UI; only user 0 manages the source-of-truth store. */
public final class IdentityActivity extends Activity {
    private static final int BLUE = UiKit.PRIMARY, TEXT = UiKit.TEXT, MUTED = UiKit.MUTED;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private IdentityStore store;
    private List<IdentityBackend.Space> spaces = new ArrayList<>();
    private final Map<Integer, Integer> appCounts = new HashMap<>();
    private IdentityBackend.Space selected;
    private JSONObject workspace;
    private LinearLayout page;
    private TextView stateView;
    private boolean busy;
    private boolean workspaceLocked;
    private String status = "正在读取工作空间…";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        UiKit.configureWindow(this);
        if (android.os.Process.myUid() / 100000 != 0) { finish(); return; }
        try { store = new IdentityStore(this); } catch (Exception e) { error(e); return; }
        workspaceLocked = getIntent().hasExtra("userId");
        render(); loadSpaces(getIntent().getIntExtra("userId", -1));
    }

    private interface Job { String run() throws Exception; }
    private void task(Job job, Runnable after) {
        if (busy) { toast("请等待当前操作完成"); return; }
        busy = true; if (stateView != null) stateView.setText("正在处理，请稍候…");
        executor.execute(() -> {
            String message; Exception failure = null;
            try { message = job.run(); } catch (Exception e) { message = e.getMessage(); failure = e; }
            final String result = message; final Exception error = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false; status = result == null ? "操作完成" : result;
                if (error != null) error(error);
                if (after != null && error == null) after.run(); else reloadWorkspace();
            });
        });
    }

    private void loadSpaces(int preferred) {
        task(() -> {
            spaces = IdentityBackend.spaces(); appCounts.clear();
            for (IdentityBackend.Space s : spaces) {
                try { appCounts.put(s.id, IdentityBackend.apps(s.id).size()); }
                catch (Exception ignored) { appCounts.put(s.id, -1); }
            }
            return "";
        }, () -> {
            selected = null;
            for (IdentityBackend.Space s : spaces) if (s.id == preferred) selected = s;
            if (selected == null && !workspaceLocked && !spaces.isEmpty()) selected = spaces.get(0);
            reloadWorkspace();
        });
    }

    private void reloadWorkspace() {
        try { workspace = selected == null ? null : store.workspace(selected.id, selected.serial); render(); }
        catch (Exception e) { error(e); }
    }

    private void render() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(UiKit.BACKGROUND);
        page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(16), dp(18), dp(16), dp(34));
        scroll.addView(page); setContentView(scroll);
        TextView back = label("‹  返回工作空间", 14, BLUE); back.setPadding(0, 0, 0, dp(15)); back.setOnClickListener(v -> finish()); page.addView(back);
        TextView title = label(workspaceLocked ? "设备信息" : "设备配置", 28, TEXT); title.setTypeface(null, Typeface.BOLD); page.addView(title);
        page.addView(note(workspaceLocked
                ? "这里只管理当前空间；不会影响其他工作空间"
                : "每个空间独立一套，同一空间内的第三方应用自动共用"));
        stateView = null;
        if (status != null && !status.trim().isEmpty()) {
            stateView = pill(status, UiKit.NEUTRAL_CONTAINER, MUTED);
            stateView.setMaxLines(2);
            stateView.setMinHeight(dp(36));
            stateView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
            stateParams.bottomMargin = dp(10); page.addView(stateView, stateParams);
        }

        boolean engineActive = EngineStatus.isActive();
        if (spaces.isEmpty()) {
            LinearLayout empty = card();
            TextView emptyTitle = label(busy ? "正在读取工作空间" : "还没有可配置的工作空间", 18, TEXT);
            emptyTitle.setTypeface(null, Typeface.BOLD); empty.addView(emptyTitle);
            empty.addView(note(busy ? "正在连接 Core 并读取应用列表，请稍候。" : "请先返回创建工作空间；如果已经创建，请确认 Core 已升级并重新打开 UsersHub。"));
            if (busy) empty.addView(new ProgressBar(this));
            page.addView(empty);
            LinearLayout engineOnly = card();
            engineOnly.addView(pill(engineActive ? "设备模拟服务正常" : "需要在 LSPosed 中启用 UsersHub",
                    engineActive ? UiKit.SUCCESS_CONTAINER : UiKit.DANGER_CONTAINER,
                    engineActive ? UiKit.SUCCESS : UiKit.DANGER));
            row(engineOnly, button("了解功能与限制", this::showLimits));
            page.addView(engineOnly);
            return;
        }

        if (workspaceLocked && selected == null) {
            LinearLayout unavailable = card();
            TextView unavailableTitle = label("这个工作空间已不可用", 18, TEXT);
            unavailableTitle.setTypeface(null, Typeface.BOLD); unavailable.addView(unavailableTitle);
            unavailable.addView(note("它可能已被删除。请返回工作空间列表重新选择。"));
            page.addView(unavailable);
            return;
        }

        if (workspaceLocked) {
            page.addView(sectionTitle("当前工作空间"));
            LinearLayout currentSpace = new LinearLayout(this);
            currentSpace.setGravity(Gravity.CENTER_VERTICAL);
            currentSpace.setPadding(dp(16), 0, dp(12), 0);
            currentSpace.setBackground(UiKit.background(this, UiKit.SURFACE, 14, UiKit.BORDER));
            TextView currentSpaceName = label("空间 " + selected.id, 15, TEXT);
            currentSpaceName.setTypeface(null, Typeface.BOLD);
            currentSpace.addView(currentSpaceName, new LinearLayout.LayoutParams(0, -2, 1));
            currentSpace.addView(pill("当前空间", UiKit.PRIMARY_CONTAINER, BLUE));
            LinearLayout.LayoutParams currentSpaceParams = new LinearLayout.LayoutParams(-1, dp(52));
            currentSpaceParams.bottomMargin = dp(4); page.addView(currentSpace, currentSpaceParams);
        } else {
            page.addView(sectionTitle("选择工作空间"));
            FrameLayout pickerContainer = new FrameLayout(this);
            pickerContainer.setBackground(UiKit.background(this, UiKit.SURFACE, 14, UiKit.BORDER));
            Spinner picker = new Spinner(this);
            picker.setPadding(dp(12), 0, dp(48), 0);
            picker.setBackgroundColor(Color.TRANSPARENT);
            ArrayAdapter<IdentityBackend.Space> pickerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spaces);
            pickerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            picker.setAdapter(pickerAdapter);
            int selectedIndex = selected == null ? 0 : spaces.indexOf(selected); picker.setSelection(Math.max(0, selectedIndex));
            picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onNothingSelected(AdapterView<?> parent) {}
                @Override public void onItemSelected(AdapterView<?> p, View v, int position, long id) {
                    if (!busy && selected != spaces.get(position)) { selected = spaces.get(position); reloadWorkspace(); }
                }
            });
            pickerContainer.addView(picker, new FrameLayout.LayoutParams(-1, -1));
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(com.captainsss.usershub.R.drawable.ic_action_expand_more);
            arrow.setImageTintList(android.content.res.ColorStateList.valueOf(MUTED));
            arrow.setContentDescription("展开工作空间列表");
            arrow.setClickable(false); arrow.setFocusable(false);
            FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.END | Gravity.CENTER_VERTICAL);
            arrowParams.rightMargin = dp(15); pickerContainer.addView(arrow, arrowParams);
            LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(-1, dp(52));
            pickerParams.bottomMargin = dp(4); page.addView(pickerContainer, pickerParams);
        }
        if (workspace == null) return;
        LinearLayout controls = card();
        JSONArray profiles = workspace.optJSONArray("profiles");
        int fields = profiles.length() == 0 ? 0 : profiles.optJSONObject(0).optJSONArray("enabledFields").length();
        int appCount = appCounts.containsKey(selected.id) ? appCounts.get(selected.id) : -1;
        LinearLayout currentHeader = new LinearLayout(this); currentHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView currentTitle = label("空间 " + selected.id + " 的设备配置", 19, TEXT); currentTitle.setTypeface(null, Typeface.BOLD);
        currentHeader.addView(currentTitle, new LinearLayout.LayoutParams(0, -2, 1));
        currentHeader.addView(pill(workspace.optBoolean("enabled") ? "已启用" : "未启用",
                workspace.optBoolean("enabled") ? UiKit.SUCCESS_CONTAINER : UiKit.NEUTRAL_CONTAINER,
                workspace.optBoolean("enabled") ? UiKit.SUCCESS : UiKit.NEUTRAL));
        controls.addView(currentHeader);
        String profileSummary = profiles.length() == 0 ? "设备资料尚未生成" : "设备资料已生成 · 启用 " + fields + " 项";
        String appSummary = appCount < 0 ? "适用应用暂未读取" : appCount + " 个第三方应用会使用";
        controls.addView(note(profileSummary + " · " + appSummary + " · "
                + (engineActive ? "模拟服务正常" : "模拟服务需要设置")));
        Switch enabled = new Switch(this); enabled.setText("为本空间应用启用设备信息模拟"); enabled.setTextSize(15);
        enabled.setChecked(workspace.optBoolean("enabled")); enabled.setPadding(0, dp(8), 0, dp(8));
        enabled.setOnCheckedChangeListener((b, checked) -> {
            if (busy) { reloadWorkspace(); return; }
            if (checked && workspace.optJSONArray("profiles").length() == 0) {
                message("尚未生成设备信息", "请先为本空间生成设备信息。"); reloadWorkspace(); return;
            }
            confirm(checked ? "开启设备模拟" : "关闭设备模拟", checked
                    ? "保存本空间的设备信息，并让本空间的第三方应用使用。系统应用和管理工具不参与，也不会自动打开空间或应用。"
                    : "设备信息会保留，但不再提供给应用。已经运行的应用可能保存了旧值，需要重新启动后才会完全恢复。",
                    () -> { try { JSONObject next = copy(workspace); next.put("enabled", checked); save(next); } catch (Exception e) { error(e); } });
        });
        controls.addView(enabled);
        controls.addView(note(workspace.optBoolean("autoInherit") ? "以后新安装的第三方应用：自动使用本空间设备信息" : "当前为旧版手动选择模式；重新生成后会改为自动使用"));
        row(controls, primary("保存并应用", () -> save(workspace)), button("检查是否生效", this::diagnostics));
        controls.addView(note("应用配置不会启动冻结空间，也不会自动打开或停止任何应用。运行中的应用需要重启后读取新资料。"));
        page.addView(controls);
        if (profiles.length() == 0) row(page, primary("为本空间生成设备资料", this::newProfile));
        for (int i = 0; i < profiles.length(); i++) profileCard(profiles.optJSONObject(i));

        if (!workspaceLocked) {
            page.addView(sectionTitle("所有空间"));
            LinearLayout batch = card();
            TextView batchTitle = label("批量生成独立设备资料", 17, TEXT); batchTitle.setTypeface(null, Typeface.BOLD); batch.addView(batchTitle);
            batch.addView(note("为每个空间分别生成 10 项基础标识。空间之间不同，同一空间内当前和以后安装的第三方应用共用。"));
            row(batch, button("为所有空间重新生成", this::batchCreate)); page.addView(batch);
        }

        LinearLayout help = card();
        help.addView(label("使用说明", 16, TEXT));
        help.addView(note("空间冻结或未启动时会先保存配置，等你正常启动后自动应用。作用域准备完成不等于服务器认可，也不代表硬件身份已改变。"));
        row(help, button("了解功能与限制", this::showLimits)); page.addView(help);

        if (!workspaceLocked) {
            LinearLayout danger = card();
            danger.addView(label("安全停用", 16, UiKit.DANGER));
            danger.addView(note("遇到应用异常时，可保留配置并停止向所有空间提供模拟信息。"));
            row(danger, dangerButton("紧急停用全部模拟", this::emergencyOff)); page.addView(danger);
        }
    }

    private void profileCard(JSONObject profile) {
        LinearLayout c = card(); String id = profile.optString("id"); int bound = 0;
        JSONObject assignments = workspace.optJSONObject("assignments"); Iterator<String> keys = assignments.keys();
        while (keys.hasNext()) if (id.equals(assignments.optString(keys.next()))) bound++;
        TextView name = label("当前设备资料", 18, TEXT); name.setTypeface(null, Typeface.BOLD); c.addView(name);
        JSONArray enabled = profile.optJSONArray("enabledFields");
        int identifiers = countEnabled(enabled, IdentityFields.IDS), build = countEnabled(enabled, IdentityFields.BUILD);
        String model = "未选择机型模板";
        try {
            DeviceTemplateCatalog.Template matched = DeviceTemplateCatalog.match(
                    DeviceTemplateCatalog.load(this), profile.optJSONObject("values"));
            if (matched != null) model = matched.name + " · Android " + matched.androidRelease();
        } catch (Exception ignored) {}
        c.addView(note(model + "\n基础标识 " + identifiers + " 项 · 机型字段 " + build + " 项 · "
                + (workspace.optBoolean("autoInherit") ? "本空间第三方应用自动共用" : bound + " 个应用正在使用")));
        row(c, primary("生成 / 更换基础标识", this::newProfile));
        row(c, button("选择机型与高级编辑", () -> editProfile(profile)), button("适用应用与重启提示", () -> showAutomaticApps(profile)));
        page.addView(c);
    }

    private int countEnabled(JSONArray enabled, String[] candidates) {
        if (enabled == null) return 0;
        Set<String> values = new HashSet<>();
        for (int i = 0; i < enabled.length(); i++) values.add(enabled.optString(i));
        int count = 0; for (String candidate : candidates) if (values.contains(candidate)) count++;
        return count;
    }

    private void newProfile() {
        if (busy || selected == null) return;
        randomize(Collections.singletonList(selected));
    }

    private void editProfile(JSONObject original) {
        if (busy) return;
        try {
            JSONObject profile = copy(original); JSONObject values = profile.getJSONObject("values");
            List<DeviceTemplateCatalog.Template> templates = DeviceTemplateCatalog.load(this);
            Set<String> enabledFields = new HashSet<>(); JSONArray enabled = profile.getJSONArray("enabledFields");
            for (int i = 0; i < enabled.length(); i++) enabledFields.add(enabled.getString(i));
            LinearLayout form = vertical(); form.setPadding(dp(20), dp(8), dp(20), dp(16));
            Map<String, EditText> inputs = new LinkedHashMap<>(); Map<String, CheckBox> switches = new LinkedHashMap<>();
            LinearLayout advanced = vertical();
            advanced.addView(sectionTitle("基础标识字段"));
            fieldEditors(advanced, IdentityFields.IDS, IdentityFields.ID_LABELS, values, enabledFields, inputs, switches);
            advanced.addView(sectionTitle("机型与构建字段"));
            fieldEditors(advanced, IdentityFields.BUILD, IdentityFields.BUILD_LABELS, values, enabledFields, inputs, switches);
            advanced.setVisibility(View.GONE);

            form.addView(label("基础标识", 18, TEXT));
            form.addView(note("默认启用 10 项空间级标识，同一空间的目标应用共用。重新生成后，正在运行的应用需要重启。"));
            Button generate = button("重新生成 10 项基础标识", () -> {
                try { JSONObject fresh = store.newProfile("临时").getJSONObject("values");
                    for (String key : IdentityFields.IDS) {
                        inputs.get(key).setText(fresh.getString(key)); switches.get(key).setChecked(true);
                    }
                } catch (Exception e) { error(e); }
            });
            form.addView(generate);
            form.addView(sectionTitle("成套机型（可选）"));
            form.addView(note("模板会保持型号、构建指纹、Build ID、增量版本、构建类型和签名标签一致。不会修改 SDK、处理器、屏幕、基带或硬件认证。"));
            DeviceTemplateCatalog.Template matched = DeviceTemplateCatalog.match(templates, values);
            TextView templateState = note(matched == null ? "当前机型：自定义或未设置" : "当前机型模板：" + matched.name + " · " + matched.badge());
            form.addView(templateState);
            row(form,
                    primary("选择机型", () -> showTemplatePicker(templates, inputs, switches, templateState)),
                    button("随机同版本机型", () -> applyRandomTemplate(templates, inputs, switches, templateState)));
            form.addView(button("使用本机真实信息", () -> applyTemplate(DeviceTemplateCatalog.currentDevice(), inputs, switches, templateState)));

            Button[] advancedToggle = new Button[1];
            advancedToggle[0] = button("展开高级字段", () -> {
                boolean show = advanced.getVisibility() != View.VISIBLE;
                advanced.setVisibility(show ? View.VISIBLE : View.GONE);
                advancedToggle[0].setText(show ? "收起高级字段" : "展开高级字段");
            });
            form.addView(advancedToggle[0]);
            form.addView(note("高级编辑适合需要逐项核对的用户。只有勾选且格式正确的字段才会提供给应用。"));
            form.addView(advanced);
            ScrollView scroll = new ScrollView(this); scroll.addView(form);
            AlertDialog dialog = new AlertDialog.Builder(this).setTitle("本空间设备资料").setView(scroll)
                    .setNegativeButton("取消", null).setPositiveButton("保存", null).create();
            dialog.setOnShowListener(x -> dialog.getButton(-1).setOnClickListener(v -> {
                try {
                    JSONObject next = copy(workspace); JSONObject edited = IdentityStore.findProfile(next, profile.getString("id"));
                    JSONObject newValues = new JSONObject(); JSONArray on = new JSONArray();
                    for (String key : inputs.keySet()) {
                        String value = inputs.get(key).getText().toString().trim();
                        if (!value.isEmpty()) { IdentityFields.validate(key, value); newValues.put(key, value); }
                        if (switches.get(key).isChecked()) { IdentityFields.validate(key, value); on.put(key); }
                    }
                    edited.put("values", newValues).put("enabledFields", on); IdentityStore.validateWorkspace(next);
                    dialog.dismiss(); save(next);
                } catch (Exception e) { error(e); }
            })); dialog.show();
        } catch (Exception e) { error(e); }
    }

    private void showTemplatePicker(List<DeviceTemplateCatalog.Template> templates, Map<String, EditText> inputs,
                                    Map<String, CheckBox> switches, TextView state) {
        final List<DeviceTemplateCatalog.Template> visible = new ArrayList<>(templates);
        LinearLayout content = vertical(); content.setPadding(dp(18), dp(4), dp(18), 0);
        EditText search = input("搜索品牌、型号、代号或系统版本", "");
        search.setSingleLine(true); content.addView(search);
        TextView count = note(visible.size() + " 套机型 · " + DeviceTemplateCatalog.compatibleRandomCount(templates)
                + " 套与本机 Android " + Build.VERSION.RELEASE + " 一致，可随机");
        content.addView(count);
        ListView list = new ListView(this); list.setDividerHeight(1); content.addView(list, new LinearLayout.LayoutParams(-1, dp(430)));
        BaseAdapter adapter = new BaseAdapter() {
            @Override public int getCount() { return visible.size(); }
            @Override public Object getItem(int position) { return visible.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override public View getView(int position, View old, ViewGroup parent) {
                DeviceTemplateCatalog.Template template = visible.get(position);
                LinearLayout row = vertical(); row.setPadding(dp(12), dp(10), dp(12), dp(10));
                TextView title = label(template.name + " · Android " + template.androidRelease(), 15, TEXT);
                title.setTypeface(null, Typeface.BOLD); row.addView(title);
                row.addView(label(template.values.get("brand") + " · " + template.values.get("model")
                        + " · " + template.values.get("device"), 12, MUTED));
                boolean compatible = template.compatibleWithCurrentDevice();
                String availability = compatible && template.randomEligible ? " · 同系统 · 可随机"
                        : compatible ? " · 同系统 · 仅手动" : " · 系统版本不同 · 仅手动";
                row.addView(label(template.badge() + availability, 11,
                        compatible && template.randomEligible ? UiKit.SUCCESS : compatible ? MUTED : UiKit.WARNING));
                return row;
            }
        };
        list.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("选择成套机型").setView(content)
                .setNeutralButton("来源说明", null).setNegativeButton("取消", null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            DeviceTemplateCatalog.Template template = visible.get(position); dialog.dismiss();
            if ("review".equals(template.verification) || !template.compatibleWithCurrentDevice()) {
                String warning = "review".equals(template.verification) ? "这套模板仍待核验。"
                        : "这套模板是 Android " + template.androidRelease() + "，本机是 Android " + Build.VERSION.RELEASE
                        + "。UsersHub 不会修改 SDK 或系统版本，混用会产生明显不一致。";
                new AlertDialog.Builder(this).setTitle("请确认模板兼容性")
                        .setMessage(warning + "\n\n" + template.note + "\n\n来源：" + template.sourceName + "\n" + template.sourceUrl)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("仍然填入", (d, w) -> applyTemplate(template, inputs, switches, state)).show();
            } else applyTemplate(template, inputs, switches, state);
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int countValue) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT); visible.clear();
                for (DeviceTemplateCatalog.Template template : templates)
                    if (query.isEmpty() || template.searchText().contains(query)) visible.add(template);
                count.setText(query.isEmpty() ? visible.size() + " 套机型 · " + DeviceTemplateCatalog.compatibleRandomCount(templates)
                        + " 套与本机 Android " + Build.VERSION.RELEASE + " 一致，可随机"
                        : "找到 " + visible.size() + " 套机型"); adapter.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(v -> showTemplateSources(templates)));
        dialog.show();
    }

    private void applyRandomTemplate(List<DeviceTemplateCatalog.Template> templates,
                                     Map<String, EditText> inputs, Map<String, CheckBox> switches, TextView state) {
        try { applyTemplate(DeviceTemplateCatalog.randomComplete(templates), inputs, switches, state); }
        catch (Exception e) { message("没有可用的随机机型", e.getMessage()); }
    }

    private void applyTemplate(DeviceTemplateCatalog.Template template, Map<String, EditText> inputs,
                               Map<String, CheckBox> switches, TextView state) {
        for (String key : IdentityFields.BUILD) {
            if ("serial".equals(key)) continue;
            inputs.get(key).setText(""); switches.get(key).setChecked(false);
        }
        for (Map.Entry<String, String> entry : template.values.entrySet()) {
            inputs.get(entry.getKey()).setText(entry.getValue()); switches.get(entry.getKey()).setChecked(true);
        }
        state.setText("已填入：" + template.name + " · " + template.badge() + " · 保存后生效");
        toast("已填入 " + template.name + "，请检查后保存");
    }

    private void showTemplateSources(List<DeviceTemplateCatalog.Template> templates) {
        StringBuilder text = new StringBuilder("官方来源：来源页面直接给出构建指纹；社区来源：通过正式签名、字段完整性，以及品牌、产品和设备代号一致性检查，但不等于对应真机证明。旧系统机型只允许手动选择。\n\n");
        Map<String, Integer> counts = new LinkedHashMap<>(); Map<String, String> urls = new LinkedHashMap<>();
        for (DeviceTemplateCatalog.Template template : templates) {
            counts.put(template.sourceName, counts.getOrDefault(template.sourceName, 0) + 1);
            urls.put(template.sourceName, template.sourceUrl);
        }
        for (String source : counts.keySet()) text.append("• ").append(source).append(" · ")
                .append(counts.get(source)).append(" 套\n").append(urls.get(source)).append("\n\n");
        message("机型模板来源与核验状态", text.toString().trim());
    }

    private void fieldEditors(LinearLayout form, String[] keys, String[] names, JSONObject values, Set<String> enabled,
                              Map<String, EditText> inputs, Map<String, CheckBox> switches) {
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i]; CheckBox box = new CheckBox(this); box.setText(names[i]); box.setChecked(enabled.contains(key));
            EditText input = input(names[i], values.optString(key)); input.setTextSize(13);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            form.addView(box); form.addView(input); switches.put(key, box); inputs.put(key, input);
        }
    }

    private void bind(JSONObject profile) {
        final List<String> packages = new ArrayList<>();
        task(() -> { packages.addAll(IdentityBackend.apps(selected.id)); return "选择需要使用此配置的应用。"; }, () -> {
            if (packages.isEmpty()) { message("没有可选应用", "请先向空间安装第三方应用。"); render(); return; }
            String id = profile.optString("id"); boolean[] checked = new boolean[packages.size()];
            for (int i = 0; i < checked.length; i++) checked[i] = id.equals(workspace.optJSONObject("assignments").optString(packages.get(i)));
            ListView list = new ListView(this); list.setAdapter(new BaseAdapter() {
                @Override public int getCount() { return packages.size(); }
                @Override public Object getItem(int i) { return packages.get(i); }
                @Override public long getItemId(int i) { return i; }
                @Override public View getView(int i, View old, ViewGroup parent) {
                    LinearLayout row = new LinearLayout(IdentityActivity.this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12), dp(8), dp(12), dp(8));
                    ImageView icon = new ImageView(IdentityActivity.this); String pkg = packages.get(i); String title = pkg;
                    try { ApplicationInfo app = getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES);
                        icon.setImageDrawable(app.loadIcon(getPackageManager())); title = app.loadLabel(getPackageManager()).toString();
                    } catch (Exception e) { icon.setImageDrawable(getPackageManager().getDefaultActivityIcon()); }
                    row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
                    LinearLayout labels = vertical(); labels.setPadding(dp(10), 0, dp(5), 0);
                    labels.addView(label(title, 14, TEXT)); labels.addView(label(pkg, 10, MUTED));
                    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
                    CheckBox box = new CheckBox(IdentityActivity.this); box.setChecked(checked[i]); box.setFocusable(false); box.setClickable(false); row.addView(box);
                    row.setOnClickListener(v -> { checked[i] = !checked[i]; notifyDataSetChanged(); }); return row;
                }
            });
            new AlertDialog.Builder(this).setTitle("空间 " + selected.id + " · " + profile.optString("name"))
                    .setView(list).setNegativeButton("取消", null).setPositiveButton("保存绑定", (d, which) -> {
                        try { JSONObject next = copy(workspace); JSONObject bindings = next.getJSONObject("assignments");
                            for (int i = 0; i < packages.size(); i++) {
                                String p = packages.get(i);
                                if (checked[i]) bindings.put(p, id); else if (id.equals(bindings.optString(p))) bindings.remove(p);
                            }
                            save(next);
                        } catch (Exception e) { error(e); }
                    }).show(); render();
        });
    }

    private void deleteProfile(JSONObject profile) {
        confirm("删除配置？", "删除配置及其应用绑定，不删除空间和应用数据。运行中的应用需重新启动以完全恢复。", () -> {
            try { JSONObject next = copy(workspace); JSONArray ps = next.getJSONArray("profiles"); String id = profile.getString("id");
                for (int i = ps.length() - 1; i >= 0; i--) if (id.equals(ps.getJSONObject(i).getString("id"))) ps.remove(i);
                JSONObject bindings = next.getJSONObject("assignments"); List<String> remove = new ArrayList<>(); Iterator<String> keys = bindings.keys();
                while (keys.hasNext()) { String key = keys.next(); if (id.equals(bindings.getString(key))) remove.add(key); }
                for (String p : remove) bindings.remove(p); if (bindings.length() == 0) next.put("enabled", false); save(next);
            } catch (Exception e) { error(e); }
        });
    }

    private void save(JSONObject source) {
        if (busy || source == null) return;
        final JSONObject next;
        try { next = copy(source); next.put("revision", UUID.randomUUID().toString()); IdentityStore.validateWorkspace(next); }
        catch (Exception e) { error(e); return; }
        task(() -> { store.save(next); return IdentityBackend.deploy(next) + "\n正在运行的应用需要重新启动后读取新信息。"; }, null);
    }

    private void batchCreate() {
        if (busy || spaces.isEmpty()) return;
        randomize(new ArrayList<>(spaces));
    }

    private void randomize(List<IdentityBackend.Space> targets) {
        confirm(targets.size() == 1 ? "重新生成空间 " + targets.get(0).id + " 的设备信息？" : "为所有工作空间生成设备信息？",
                "每个空间会生成一套独立的 10 项设备信息。同一空间的第三方应用共用，以后新安装的第三方应用也会自动使用。\n\n这会覆盖 " + targets.size() + " 个工作空间的旧设备信息，不保留备份，并自动准备适用应用。\n\n不会启动冻结空间，也不会打开或停止应用。正在运行的应用需要手动重启后读取新信息。", () -> {
            StringBuilder report = new StringBuilder();
            task(() -> {
                int failed = 0;
                for (IdentityBackend.Space s : targets) {
                    try {
                        List<String> apps = IdentityBackend.apps(s.id);
                        JSONObject next = store.replaceShared(s.id, s.serial, apps);
                        report.append(IdentityBackend.deploy(next)).append("\n");
                        List<String> names = new ArrayList<>();
                        for (String pkg : apps) names.add(appLabel(pkg));
                        appCounts.put(s.id, apps.size());
                        report.append(apps.isEmpty() ? "暂无第三方应用，以后安装的应用会自动使用" : "适用应用：" + android.text.TextUtils.join("、", names)).append("\n\n");
                    } catch (Exception e) { failed++; report.append("空间 ").append(s.id).append("：未完成 — ").append(e.getMessage()).append("\n\n"); }
                }
                report.append("正在运行的应用请手动重启；没有运行的应用不必特意打开。本次操作没有启动任何空间或应用。");
                return "已处理 " + targets.size() + " 个空间" + (failed == 0 ? "，设备信息已保存并应用。" : "，其中 " + failed + " 个未完成。");
            }, () -> { reloadWorkspace(); message("设备信息生成结果", report.toString()); });
        });
    }

    private String appLabel(String pkg) {
        try { return getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(getPackageManager()).toString(); }
        catch (Exception e) { return pkg; }
    }
    private void showAutomaticApps(JSONObject profile) {
        List<String> apps = new ArrayList<>();
        task(() -> { apps.addAll(IdentityBackend.apps(selected.id)); return "应用列表已刷新，未打开应用。"; }, () -> {
            appCounts.put(selected.id, apps.size());
            LinearLayout box = vertical(); box.setPadding(dp(18), dp(8), dp(18), dp(14));
            box.addView(note("这些第三方应用会共用本空间的设备信息。以后新安装的应用会自动加入；如果不想等待后台检查，可以点“立即应用到本空间”。系统应用和管理工具不参与。"));
            for (String pkg : apps) {
                LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0, dp(8), 0, dp(8));
                ImageView icon = new ImageView(this);
                try { icon.setImageDrawable(getPackageManager().getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadIcon(getPackageManager())); }
                catch (Exception e) { icon.setImageDrawable(getPackageManager().getDefaultActivityIcon()); }
                row.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                LinearLayout labels = vertical(); labels.setPadding(dp(12), 0, 0, 0);
                labels.addView(label(appLabel(pkg), 15, TEXT)); labels.addView(label(pkg, 10, MUTED));
                row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); box.addView(row);
            }
            box.addView(note(apps.isEmpty() ? "本空间暂无第三方应用。" : "以上应用如果正在运行，请重启后读取新设备信息；没有运行的应用无需特意打开。"));
            ScrollView scroll = new ScrollView(this); scroll.addView(box);
            new AlertDialog.Builder(this).setTitle("空间 " + selected.id + " · 适用应用").setView(scroll).setPositiveButton("知道了", null).show();
        });
    }

    private void diagnostics() {
        if (selected == null) return;
        int user = selected.id; long serial = selected.serial; String revision = workspace.optString("revision");
        final StringBuilder summary = new StringBuilder();
        final StringBuilder detail = new StringBuilder();
        task(() -> {
            RootCommand.Result result = RootCommand.run("usershubidentity status " + user + " " + serial);
            if (!result.ok) throw new Exception(result.output);
            String deployment = result.output.trim();
            summary.append("设备信息：").append(friendlyDeployment(deployment)).append("\n");
            detail.append("配置部署：").append(IdentityBackend.describeState(deployment)).append("\n");
            RootCommand.Result scope = RootCommand.run("cat /data/adb/usershub/identity/" + user + "-" + serial + ".scope.json");
            boolean scopeReady = false; int scopedApps = 0;
            if (scope.ok) {
                JSONObject s = new JSONObject(scope.output);
                scopeReady = revision.equals(s.optString("revision")) && "READY".equals(s.optString("state"));
                JSONArray packages = s.optJSONArray("packages"); scopedApps = packages == null ? 0 : packages.length();
                detail.append("作用域：").append(scopeReady ? "已核验（不代表应用已加载）" : "待同步 / 已关闭").append("\n");
                if (s.has("error")) detail.append(s.optString("error")).append("\n");
            } else detail.append("作用域：尚无同步记录\n");
            if (!workspace.optBoolean("enabled")) summary.append("适用应用：设备模拟当前已关闭\n");
            else if (scopeReady) summary.append("适用应用：已准备 ").append(scopedApps).append(" 个\n");
            else summary.append("适用应用：尚未准备完成，请点“立即应用到本空间”后重试\n");
            RootCommand.Result reports = RootCommand.run("usershubidentity receipts " + user + " " + serial);
            if (!reports.ok) throw new Exception(reports.output);
            int count = 0, verifiedApps = 0, restartApps = 0; boolean spaceStopped = false;
            StringBuilder appSummary = new StringBuilder();
            for (String line : reports.output.split("\\R")) {
                if (!line.startsWith("{")) continue;
                JSONObject r = new JSONObject(line);
                if (!r.has("package")) { spaceStopped = true; detail.append("空间未运行，未读取进程回报。\n"); continue; }
                count++; boolean current = revision.equals(r.optString("revision"));
                String pkg = r.optString("package"); JSONArray verified = r.optJSONArray("verified");
                int verifiedFields = verified == null ? 0 : verified.length();
                if (!current) { restartApps++; appSummary.append("• ").append(appLabel(pkg)).append("：需要重启后再检查\n"); }
                else if (verifiedFields > 0) { verifiedApps++; appSummary.append("• ").append(appLabel(pkg)).append("：已确认读取 ").append(verifiedFields).append(" 项\n"); }
                else appSummary.append("• ").append(appLabel(pkg)).append("：已启动，暂未发现设备信息读取记录\n");
                detail.append(pkg).append("\n").append(current ? "此配置曾被引擎加载" : "旧配置回报，等待新进程")
                        .append("\n接口调用记录：").append(fieldNames(r.optJSONArray("observed")))
                        .append("\nHook 返回值核对通过：").append(fieldNames(verified))
                        .append("\n返回冲突：").append(fieldNames(r.optJSONArray("mismatched")))
                        .append("\n时间：").append(DateFormat.getDateTimeInstance().format(new Date(r.optLong("time")))).append("\n\n");
            }
            if (count == 0) {
                summary.append("应用读取情况：暂无记录\n");
                summary.append(spaceStopped ? "提示：空间当前未运行，正常启动后再检查。\n" : "提示：请打开目标应用使用一会儿，再回来检查。\n");
                detail.append("暂无目标应用回报。请核对 LSPosed 作用域、应用绑定和空间开关，再重启目标应用。\n");
            } else {
                summary.append("应用读取情况：已检查 ").append(count).append(" 个，确认生效 ").append(verifiedApps).append(" 个");
                if (restartApps > 0) summary.append("，需要重启 ").append(restartApps).append(" 个");
                summary.append("\n").append(appSummary);
            }
            summary.append("\n本次检查没有启动或停止任何空间和应用。");
            detail.append("每条是对应进程的历史记录。返回值核对只覆盖本模块 Hook 的回调阶段，不能证明应用缓存、原生层、服务器识别或所有读取途径均被覆盖。");
            return "检查完成，没有启动或停止任何应用。";
        }, () -> { render(); diagnosticMessage("空间 " + user + " · 应用生效情况", summary.toString(), detail.toString()); });
    }

    private String friendlyDeployment(String state) {
        switch (state.trim()) {
            case "DEPLOYED": return "已送达本空间";
            case "PENDING": return "已保存，空间正常启动后自动应用";
            case "DISABLED": return "已保存，但设备模拟处于关闭状态";
            case "PAUSED": return "已被紧急停用";
            case "NOT_DEPLOYED": return "尚未应用";
            default: return state;
        }
    }

    private void showLimits() {
        message("功能与限制", "UsersHub 会为每个工作空间保存独立的基础标识；同一空间的第三方应用共用，系统应用和管理工具不参与。选择机型模板后，还会保持型号、构建指纹及其可可靠推导的构建字段一致。\n\nLSPosed 负责让 UsersHub 在适用应用进程内工作。请启用 UsersHub，并关闭其他同类设备模拟模块，避免重复修改。当前自动作用域只验证 LSPosed 1.9.2 (7024)。\n\n本功能不会修改 SDK、真实空间 ID、基带、SIM、CPU、屏幕、网络地址、Play Integrity、硬件认证或服务器账号历史。界面显示“已准备”或 Hook 返回值核对通过，也不等于服务器认可。运行中的应用需要重启后读取新资料。");
    }

    private void stopTargets() {
        if (busy || workspace == null) return;
        List<String> targets = new ArrayList<>(); Iterator<String> keys = workspace.optJSONObject("assignments").keys();
        while (keys.hasNext()) targets.add(keys.next()); int user = selected.id;
        if (targets.isEmpty()) { toast("尚未绑定应用"); return; }
        confirm("停止已绑定的 " + targets.size() + " 个应用？", "只停止空间 " + user + " 内这些应用，不清除数据，不启动空间。之后请从桌面重新打开应用。", () -> task(() -> {
            for (String pkg : targets) {
                RootCommand.Result r = RootCommand.run("am force-stop --user " + user + " " + RootCommand.quote(pkg));
                if (!r.ok) throw new Exception(r.output);
            }
            return "已停止目标应用；请正常启动空间并重新打开应用。";
        }, null));
    }

    private String fieldNames(JSONArray fields) {
        if (fields == null || fields.length() == 0) return "暂无记录";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < fields.length(); i++) names.add(IdentityFields.label(fields.optString(i)));
        return android.text.TextUtils.join("、", names);
    }

    private void emergencyOff() {
        confirm("紧急停用全部设备模拟？", "不删除空间、配置或应用数据。Core 会停止提供模拟配置，目标应用重新启动后完全恢复。", () -> task(() -> {
            RootCommand.Result r = RootCommand.run("usershubidentity emergency-off");
            if (!r.ok) throw new Exception(r.output);
            for (JSONObject w : store.allWorkspaces()) { w.put("enabled", false).put("revision", UUID.randomUUID().toString()); store.save(w); }
            return r.output;
        }, null));
    }

    private void exportBackup() {
        if (busy) return;
        confirm("备份说明", "备份包含模拟标识和应用绑定，请妥善保存。不会包含应用账号或应用私有数据。", () -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_TITLE, "UsersHub-device-profiles.json"); startActivityForResult(i, 70);
        });
    }
    private void importBackup() {
        if (busy) return;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"}); startActivityForResult(i, 71);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return; Uri uri = data.getData();
        if (request == 70) task(() -> {
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("无法打开备份文件"); out.write(store.exportBackup().getBytes(StandardCharsets.UTF_8));
            } return "备份已导出。";
        }, null);
        if (request == 71) {
            final String[] source = new String[1];
            task(() -> { source[0] = IdentityStore.read(getContentResolver().openInputStream(uri)); IdentityStore.parse(source[0]); return "备份格式已校验。"; }, () -> {
                render(); confirm("恢复设备配置？", "相同空间实例的配置将被备份覆盖，并默认关闭。旧实例不会套用到新空间。不会自动启动空间或清除应用数据。", () -> task(() -> {
                    JSONObject imported = IdentityStore.parse(source[0]);
                    store.importBackup(source[0]); int failed = 0;
                    for (IdentityBackend.Space s : spaces) if (imported.getJSONObject("workspaces").has(IdentityStore.key(s.id, s.serial))) {
                        try { IdentityBackend.deploy(store.workspace(s.id, s.serial)); } catch (Exception e) { failed++; }
                    }
                    return failed == 0 ? "备份已恢复，恢复的配置默认关闭。" : "备份已保存，但 " + failed + " 个空间部署失败，请重新部署或紧急停用。";
                }, null));
            });
        }
    }

    private JSONObject copy(JSONObject object) throws Exception { return new JSONObject(object.toString()); }
    private void confirm(String title, String text, Runnable action) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setNegativeButton("取消", (d,w) -> reloadWorkspace())
                .setPositiveButton("确认", (d,w) -> action.run()).setOnCancelListener(d -> reloadWorkspace()).show();
    }
    private void error(Exception e) { message("操作未完成", e.getMessage() == null ? e.toString() : e.getMessage()); }
    private void message(String title, String content) {
        TextView text = label(content == null ? "未知错误" : content, 14, TEXT); text.setPadding(dp(22), dp(8), dp(22), dp(16)); text.setLineSpacing(dp(4), 1); text.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("知道了", null).show();
    }
    private void diagnosticMessage(String title, String summary, String technical) {
        TextView text = label(summary, 14, TEXT); text.setPadding(dp(22), dp(8), dp(22), dp(16)); text.setLineSpacing(dp(4), 1); text.setTextIsSelectable(true);
        ScrollView scroll = new ScrollView(this); scroll.addView(text);
        new AlertDialog.Builder(this).setTitle(title).setView(scroll)
                .setNeutralButton("高级诊断", (d, w) -> message("高级诊断（技术信息）", technical))
                .setPositiveButton("知道了", null).show();
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(float n) { return UiKit.dp(this, n); }
    private LinearLayout vertical() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout padded(View v) { LinearLayout l = vertical(); l.setPadding(dp(20), dp(8), dp(20), dp(8)); l.addView(v); return l; }
    private TextView label(String text, int size, int color) { return UiKit.text(this, text, size, false, color); }
    private TextView note(String text) { TextView t = label(text, 12, MUTED); t.setPadding(0, dp(8), 0, dp(10)); t.setLineSpacing(dp(3), 1); return t; }
    private TextView sectionTitle(String text) { return UiKit.sectionTitle(this, text); }
    private TextView pill(String text, int fill, int color) { return UiKit.pill(this, text, fill, color); }
    private EditText input(String hint, String value) { EditText e = new EditText(this); e.setHint(hint); e.setText(value); e.setSingleLine(true); return e; }
    private LinearLayout card() {
        LinearLayout c = UiKit.card(this, 18, 15);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(10); p.bottomMargin = dp(6); c.setLayoutParams(p); return c;
    }
    private Button button(String text, Runnable action) {
        Button b = UiKit.button(this, text, UiKit.SURFACE, BLUE, UiKit.BORDER);
        b.setOnClickListener(v -> { if (!busy) action.run(); else toast("请等待当前操作完成"); }); return b;
    }
    private Button primary(String text, Runnable action) {
        Button b = UiKit.button(this, text, BLUE, Color.WHITE, Color.TRANSPARENT);
        b.setOnClickListener(v -> { if (!busy) action.run(); else toast("请等待当前操作完成"); }); return b;
    }
    private Button dangerButton(String text, Runnable action) {
        Button b = UiKit.button(this, text, UiKit.DANGER_CONTAINER, UiKit.DANGER, UiKit.DANGER);
        b.setOnClickListener(v -> { if (!busy) action.run(); else toast("请等待当前操作完成"); }); return b;
    }
    private void row(LinearLayout parent, Button... buttons) {
        LinearLayout row = new LinearLayout(this); row.setPadding(0, dp(5), 0, dp(5));
        for (int i = 0; i < buttons.length; i++) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1); if (i > 0) p.leftMargin = dp(8); row.addView(buttons[i], p); }
        parent.addView(row);
    }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
