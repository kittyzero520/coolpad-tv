package com.guardian.desktop;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v5.1 电视仪表盘（1920x1080，全部使用 v4.2 验证过的渲染安全模式：纵向 ScrollView + WRAP 高度）：
 * 顶部信息带：守护状态 | 开机时间 | 已运行 | 统计（右上角区域）
 * 第二行：保护窗口说明 | 一键清理后台
 * 下方：已禁用应用明细（个数 + 列表）
 */
public class MainActivity extends Activity {

    private static final int PINK_DARK = 0xFFC2185B;
    private static final int PINK_MAIN = 0xFFE91E63;
    private static final int PINK_TEXT = 0xFFAD6B8A;
    private static final int PINK_BORDER = 0xFFF8BBD0;
    private static final int BG = 0xFFFDF2F7;
    private static final int TEXT_DARK = 0xFF333333;
    private static final int TEXT_GRAY = 0xFF888888;

    private TextView statusValue, bootValue, upValue, statValue, windowValue;
    private TextView listCount;
    private LinearLayout listContainer;
    private Handler handler;
    private SimpleDateFormat fmtShort;
    private boolean running = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        handler = new Handler(Looper.getMainLooper());
        fmtShort = new SimpleDateFormat("HH:mm", Locale.CHINA);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));
        scroll.addView(root);

        // ===== 标题行 =====
        TextView title = new TextView(this);
        title.setText("桌面守护");
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 60);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(PINK_MAIN);
        title.setGravity(Gravity.CENTER);
        root.addView(title, rowLp(0));

        // ===== 顶部信息带：4 张等宽卡片 =====
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);

        statusValue = infoCard(infoRow, "守护状态", 44, 0);
        bootValue = infoCard(infoRow, "开机时间", 40, 0);
        upValue = infoCard(infoRow, "已运行", 44, 0);
        statValue = infoCard(infoRow, "统计数据", 40, 0);

        root.addView(infoRow, rowLp(dp(18)));

        // ===== 开机守护说明行（整行宽） =====
        LinearLayout windowRow = new LinearLayout(this);
        windowRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout winCard = card();
        windowValue = cardLine(winCard, "开机守护（保护窗口）", 40);
        TextView winNote = new TextView(this);
        winNote.setText("开机后 10 分钟内自动切回当贝\n之后不再干预任何应用");
        winNote.setTextSize(TypedValue.COMPLEX_UNIT_PX, 24);
        winNote.setTextColor(TEXT_GRAY);
        winNote.setLineSpacing(dp(3), 1);
        winNote.setPadding(0, dp(6), 0, 0);
        winCard.addView(winNote);
        windowRow.addView(winCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(windowRow, rowLp(dp(16)));

        // ===== 已禁用应用明细 =====
        LinearLayout listCard = card();
        LinearLayout countRow = new LinearLayout(this);
        countRow.setOrientation(LinearLayout.HORIZONTAL);
        countRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView listHeading = new TextView(this);
        listHeading.setText("已禁用应用明细");
        listHeading.setTextSize(TypedValue.COMPLEX_UNIT_PX, 32);
        listHeading.setTypeface(Typeface.DEFAULT_BOLD);
        listHeading.setTextColor(PINK_DARK);
        countRow.addView(listHeading);
        listCount = new TextView(this);
        listCount.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30);
        listCount.setTypeface(Typeface.DEFAULT_BOLD);
        listCount.setTextColor(PINK_MAIN);
        listCount.setPadding(dp(16), 0, 0, 0);
        countRow.addView(listCount, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        listCard.addView(countRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setFocusable(true);
        listContainer.setFocusableInTouchMode(true);
        listContainer.setPadding(dp(4), dp(10), dp(4), dp(4));
        listCard.addView(listContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(listCard, rowLp(dp(16)));

        TextView footer = new TextView(this);
        footer.setText("22 个常驻进程运行中 · 本页面仅展示状态，无需操作");
        footer.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21);
        footer.setTextColor(PINK_TEXT);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(16), 0, 0);
        root.addView(footer, rowLp(0));

        setContentView(scroll);

        loadDisabledApps();
        refresh();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!running) return;
                refresh();
                handler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /** 一键清理功能已按用户要求移除（2026-10-06）：效果有限（大户会自动重启） */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        running = false;
        handler.removeCallbacksAndMessages(null);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(2), PINK_BORDER);
        card.setBackground(bg);
        card.setPadding(dp(22), dp(14), dp(22), dp(18));
        return card;
    }

    /** 信息带小卡：统一紧凑高度，内容垂直居中，四张等大 */
    private TextView infoCard(LinearLayout row, String heading, int valueSizeSp, int leftMarginDp) {
        LinearLayout card = card();
        card.setGravity(Gravity.CENTER_VERTICAL);
        TextView v = cardLine(card, heading, valueSizeSp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, dp(108), 1f);
        if (leftMarginDp > 0) lp.leftMargin = dp(leftMarginDp);
        row.addView(card, lp);
        return v;
    }

    private TextView cardLine(LinearLayout card, String heading, int valuePx) {
        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextSize(TypedValue.COMPLEX_UNIT_PX, 26);
        h.setTextColor(PINK_TEXT);
        card.addView(h);

        TextView v = new TextView(this);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, valuePx);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(TEXT_DARK);
        v.setPadding(0, dp(2), 0, dp(2));
        card.addView(v);
        return v;
    }

    private LinearLayout.LayoutParams rowLp(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    private void refresh() {
        boolean enabled = isGuardianEnabled();
        boolean bound = GuardianService.serviceStartTime > 0;
        if (enabled && bound) {
            statusValue.setText("✓ 运行中");
        } else if (enabled) {
            statusValue.setText("连接中…");
        } else {
            statusValue.setText("✗ 未启用");
        }

        long up = SystemClock.elapsedRealtime();
        long bootAt = System.currentTimeMillis() - up;
        bootValue.setText(fmtShort.format(new Date(bootAt)));
        upValue.setText(fmtDurCompact(up));

        long start = GuardianService.serviceStartTime;
        if (start == 0) {
            windowValue.setText("等待连接…");
        } else {
            long remain = GuardianService.GRACE_PERIOD - (System.currentTimeMillis() - start);
            windowValue.setText(remain > 0 ? "剩余 " + fmtDur(remain) : "已结束 · 静默");
        }
    }

    private void loadDisabledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(0);
        List<String[]> lines = new ArrayList<>();
        for (ApplicationInfo ai : apps) {
            try {
                int st = pm.getApplicationEnabledSetting(ai.packageName);
                if (st == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                        || st == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                    lines.add(new String[]{ai.loadLabel(pm).toString(), ai.packageName});
                }
            } catch (Exception ignored) {
            }
        }

        statValue.setText("禁用 " + lines.size() + " 个");
        listCount.setText("共 " + lines.size() + " 个 · 可随时还原");

        // 每行：左侧名称 + 右侧包名，单行不换行
        listContainer.removeAllViews();
        int i = 1;
        for (String[] item : lines) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(4), dp(5), dp(4), dp(5));
            row.setFocusable(true);
            row.setFocusableInTouchMode(true);

            TextView name = new TextView(this);
            name.setTextSize(TypedValue.COMPLEX_UNIT_PX, 27);
            name.setTextColor(TEXT_DARK);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setText(String.valueOf(i++) + ". " + item[0]);
            row.addView(name, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView pkg = new TextView(this);
            pkg.setTextSize(TypedValue.COMPLEX_UNIT_PX, 21);
            pkg.setTextColor(TEXT_GRAY);
            pkg.setSingleLine(true);
            pkg.setEllipsize(TextUtils.TruncateAt.END);
            pkg.setGravity(Gravity.RIGHT);
            pkg.setText(item[1]);
            LinearLayout.LayoutParams pkgLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            pkgLp.leftMargin = dp(24);
            row.addView(pkg, pkgLp);

            listContainer.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    private boolean isGuardianEnabled() {
        try {
            String services = Settings.Secure.getString(
                    getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return services != null && services.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /** 紧凑时长（不含秒），用于顶部小卡防折行 */
    private static String fmtDurCompact(long ms) {
        long m = ms / 60000L;
        long d = m / 1440;
        m %= 1440;
        long h = m / 60;
        m %= 60;
        if (d > 0) return d + "天" + h + "时" + m + "分";
        if (h > 0) return h + "小时" + m + "分";
        return m + "分钟";
    }

    private static String fmtDur(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        s %= 86400;
        long h = s / 3600;
        s %= 3600;
        long m = s / 60;
        s %= 60;
        if (d > 0) return d + "天" + h + "小时" + m + "分" + s + "秒";
        if (h > 0) return h + "小时" + m + "分" + s + "秒";
        return m + "分" + s + "秒";
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
