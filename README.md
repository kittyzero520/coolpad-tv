# 酷派电视精简与桌面替换

针对酷派电视（Coocaa，创维代工，型号 8R134_P60P / rtk2885n，Android 11）的精简、桌面替换与桌面守护方案。

## 仓库内容

| 目录/文件 | 说明 |
|---|---|
| `酷派电视精简与桌面替换操作手册.md` | 完整操作手册：44 个应用禁用清单、桌面替换步骤、还原方法、验证结果 |
| `DesktopGuardian/` | 桌面守护 App 源码与已签名 APK |

## DesktopGuardian（桌面守护）

一个轻量无障碍服务 App，解决酷派电视开机后系统桌面（`com.coocaa.aigc.home` / `com.skyworth.officehomepage`）抢前台的问题。

核心逻辑（`GuardianService.java`）：

- **只盯目标**：仅当系统桌面抢前台时才切回当贝桌面（`com.dangbei.tvlauncher`），其他 App 一律不干预
- **10 分钟保护窗口**：仅在服务连接后 10 分钟内生效，之后完全静默，不占用任何资源
- **三重切换**：GLOBAL_ACTION_HOME → HOME Intent → 直接启动当贝，带 3 秒冷却防抖
- **事件驱动 + 轮询兜底**：主判据用 `TYPE_WINDOW_STATE_CHANGED` 事件包名，绕开 `getWindows()` 间歇返回 null 的问题

### 使用

```bash
adb install DesktopGuardian/DesktopGuardian.apk
# 安装后在 系统 → 无障碍 中开启「桌面守护」服务
```

### 构建

无 Gradle，使用 aapt2 + javac + d8 手动构建（v6.2 / versionCode 14，minSdk 21，targetSdk 30）：

```bash
# 1. 编译资源
aapt2 compile --dir res -o compiled.flata
aapt2 link -o resources.unsigned.apk -I $ANDROID_JAR --manifest AndroidManifest.xml compiled.flata --auto-add-overlay

# 2. 编译 Java
javac -source 8 -target 8 -classpath $ANDROID_JAR -d obj $(find src -name "*.java")

# 3. 转 dex
d8 --min-api 21 --output . $(find obj -name "*.class")

# 4. 打包
apkbuilder 或 zip 合并 resources.unsigned.apk + classes.dex

# 5. 对齐 + 签名
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks debug.keystore --out DesktopGuardian.apk aligned.apk
```
