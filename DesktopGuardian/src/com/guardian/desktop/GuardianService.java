package com.guardian.desktop;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

/**
 * v3 核心改动：
 * 1. 只盯目标：仅当系统桌面（aigc.home / officehomepage）抢前台时才切回当贝，
 *    其他任何 App（影视/设置/商店）一律不干预，用户可自由使用。
 * 2. 开机保护窗口：仅在服务连接后 10 分钟内生效，之后完全静默。
 *    这两个系统桌面只会被开机机制拉起，不会在平时出现。
 * 3. 主判据用 TYPE_WINDOW_STATE_CHANGED 事件自带的包名（不依赖 getWindows，
 *    彻底绕开间歇性返回 null 的问题），轮询仅作兜底。
 */
public class GuardianService extends AccessibilityService {

    private static final String TAG = "DesktopGuardian";
    private static final String DANGBEI_PKG = "com.dangbei.tvlauncher";
    private static final String DANGBEI_ACTIVITY = "com.dangbei.launcher.ui.main.MainActivity";
    private static final String AIGC_HOME = "com.coocaa.aigc.home";
    private static final String OFFICE_HOME = "com.skyworth.officehomepage";
    private static final String SKY_TV = "com.skyworth.tv"; // 开机拉起 SkyTVMainActivity 的另一个入口
    public static final long GRACE_PERIOD = 10 * 60 * 1000L; // 10 分钟保护窗口
    private static final int CHECK_DELAY = 2000;
    private static final int SWITCH_COOLDOWN = 3000;

    /** 服务本次连接时间（0=未连接），供 MainActivity 展示状态 */
    public static volatile long serviceStartTime = 0;

    private Handler handler;
    private boolean isSwitching = false;
    private long lastSwitchTime = 0;

    private static boolean isSystemLauncher(String pkg) {
        return AIGC_HOME.equals(pkg) || OFFICE_HOME.equals(pkg) || SKY_TV.equals(pkg);
    }

    private boolean inGracePeriod() {
        return System.currentTimeMillis() - serviceStartTime < GRACE_PERIOD;
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        serviceStartTime = System.currentTimeMillis();
        Log.i(TAG, "无障碍服务已连接，保护窗口 10 分钟");
        handler = new Handler(Looper.getMainLooper());

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            info.notificationTimeout = 500;
            setServiceInfo(info);
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndSwitch();
            }
        }, CHECK_DELAY);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (!inGracePeriod()) return;

        String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
        // 只对系统桌面抢前台做出反应
        if (isSystemLauncher(pkg)) {
            Log.i(TAG, "保护窗口内检测到系统桌面: " + pkg + "，切换到当贝");
            switchToDangbei();
        }
    }

    private void switchToDangbei() {
        if (isSwitching) return;
        long now = System.currentTimeMillis();
        if (now - lastSwitchTime < SWITCH_COOLDOWN) return;

        isSwitching = true;
        lastSwitchTime = System.currentTimeMillis();

        // 方法1: GLOBAL_ACTION_HOME
        boolean result = performGlobalAction(GLOBAL_ACTION_HOME);
        Log.i(TAG, "GLOBAL_ACTION_HOME: " + result);

        // 方法2: HOME Intent
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    Log.i(TAG, "已发送 HOME Intent");
                } catch (Exception e) {
                    Log.e(TAG, "HOME Intent 失败: " + e.getMessage());
                }
            }
        }, 500);

        // 方法3: 直接启动当贝桌面
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent intent = new Intent();
                    intent.setClassName(DANGBEI_PKG, DANGBEI_ACTIVITY);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                    Log.i(TAG, "已启动当贝桌面");
                } catch (Exception e) {
                    Log.e(TAG, "启动当贝失败: " + e.getMessage());
                }
                isSwitching = false;
            }
        }, 1000);
    }

    /** 轮询兜底：只在保护窗口内、且前台确认为系统桌面时才动作 */
    private void checkAndSwitch() {
        // 窗口过期：不再排程，彻底静默
        if (!inGracePeriod()) {
            Log.i(TAG, "保护窗口结束，进入静默");
            return;
        }

        if (!isSwitching && System.currentTimeMillis() - lastSwitchTime >= SWITCH_COOLDOWN) {
            try {
                String currentPkg = null;

                java.util.List<AccessibilityWindowInfo> windows = getWindows();
                if (windows != null) {
                    for (AccessibilityWindowInfo window : windows) {
                        if (window.isActive()) {
                            android.view.accessibility.AccessibilityNodeInfo root = window.getRoot();
                            if (root != null && root.getPackageName() != null) {
                                currentPkg = root.getPackageName().toString();
                                break;
                            }
                        }
                    }
                }

                if (currentPkg == null) {
                    try {
                        android.view.accessibility.AccessibilityNodeInfo root = getRootInActiveWindow();
                        if (root != null && root.getPackageName() != null) {
                            currentPkg = root.getPackageName().toString();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }

                // null 时不做任何动作（避免误切到用户正在用的 App）
                if (isSystemLauncher(currentPkg)) {
                    Log.i(TAG, "轮询检测到系统桌面: " + currentPkg + "，切换到当贝");
                    switchToDangbei();
                }
            } catch (Exception e) {
                Log.e(TAG, "checkAndSwitch error: " + e.getMessage());
            }
        }

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkAndSwitch();
            }
        }, CHECK_DELAY);
    }

    @Override
    public void onInterrupt() {
        Log.i(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        Log.i(TAG, "GuardianService onDestroy");
    }
}
