package io.github.lazyimmortal.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "io.github.lazyimmortal.seedum";
    private static final String LOG_FILE = "/sdcard/SeedumBypass.log";

    /** 写日志到文件 + logcat */
    private static void log(String msg) {
        XposedBridge.log("SeedumBypass: " + msg);
        writeFile(msg);
    }

    /** 追加写入 /sdcard/SeedumBypass.log */
    private static void writeFile(String msg) {
        try {
            File f = new File(LOG_FILE);
            // 文件超过 2MB 就清空重写
            if (f.length() > 2 * 1024 * 1024) {
                f.delete();
            }
            FileWriter fw = new FileWriter(f, true);
            String time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            fw.write(time + "  " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        // 每次注入都清空旧日志
        new File(LOG_FILE).delete();
        log("======= 开始注入 =======");

        // 方案1：Hook Toast（很多 APP 用这个）
        hookToast(lpparam);

        // 方案2：Hook Snackbar（Material Design 常用）
        hookSnackbar(lpparam);

        // 方案3：Hook TextView.setText（直接显示文字）
        hookTextView(lpparam);

        // 方案4：Hook Dialog / AlertDialog（弹窗提示）
        hookDialog(lpparam);

        // 方案5：Hook 所有 Activity 的 onCreate，看是哪个 Activity 在显示授权提示
        hookAllActivities(lpparam);

        log("======= 注入完成 =======");
        log("如果打开芝麻派后日志没有新增内容，说明 Hook 没命中。");
        log("请尝试操作芝麻派，然后下拉刷新 MT 管理器里的 SeedumBypass.log");
    }

    /**
     * 方案1：Hook Toast.makeText
     */
    private void hookToast(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> toastClass = XposedHelpers.findClass("android.widget.Toast", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(toastClass, "makeText",
                    android.content.Context.class,
                    CharSequence.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String text = param.args[1].toString();
                            log("[TOAST] 内容: " + text);
                            // 打印调用堆栈（找是谁调用的）
                            StringBuilder sb = new StringBuilder("[TOAST] 调用堆栈:\n");
                            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                                sb.append("    at ").append(ste.getClassName())
                                  .append(".").append(ste.getMethodName())
                                  .append("(").append(ste.getFileName()).append(":")
                                  .append(ste.getLineNumber()).append(")\n");
                            }
                            log(sb.toString());
                        }
                    });
            log("✅ Toast Hook 已启用");
        } catch (Throwable t) {
            log("❌ Toast Hook 失败: " + t.getMessage());
        }
    }

    /**
     * 方案2：Hook Snackbar（Android 官方推荐替代 Toast）
     */
    private void hookSnackbar(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook Snackbar.make()
            Class<?> snackbarClass = XposedHelpers.findClass("com.google.android.material.snackbar.Snackbar", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(snackbarClass, "make",
                    android.view.View.class, CharSequence.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String text = param.args[1].toString();
                            log("[SNACKBAR] 内容: " + text);
                            log("[SNACKBAR] 调用堆栈:");
                            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                                log("    at " + ste.getClassName() + "." + ste.getMethodName());
                            }
                        }
                    });
            log("✅ Snackbar.make Hook 已启用");
        } catch (Throwable t) {
            // 可能没用 Material Design，忽略
            log("ℹ️ Snackbar Hook 跳过（APK 可能不含 Material Design）");
        }

        // 也试试 AndroidX 的 Snackbar
        try {
            Class<?> snackbarClass2 = XposedHelpers.findClass("com.google.android.material.snackbar.BaseTransientBottomBar", lpparam.classLoader);
            log("ℹ️ 找到 BaseTransientBottomBar，但暂不 Hook");
        } catch (Throwable ignored) {}
    }

    /**
     * 方案3：Hook TextView.setText（直接设置文字）
     */
    private void hookTextView(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> tvClass = XposedHelpers.findClass("android.widget.TextView", lpparam.classLoader);
            // Hook setText(CharSequence)
            XposedHelpers.findAndHookMethod(tvClass, "setText",
                    CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String text = param.args[0].toString();
                            if (text.contains("授权") || text.contains("电报")
                                    || text.contains("Telegram") || text.contains("未授权")
                                    || text.contains("unauthorized") || text.contains("验证")) {
                                log("[TEXTVIEW] 设置文字: " + text);
                                log("[TEXTVIEW] 调用堆栈:");
                                for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                                    String cn = ste.getClassName();
                                    if (cn.contains("io.github.lazyimmortal.seedum") || cn.contains("seedum")) {
                                        log("    at " + cn + "." + ste.getMethodName());
                                    }
                                }
                            }
                        }
                    });
            log("✅ TextView.setText Hook 已启用");
        } catch (Throwable t) {
            log("❌ TextView Hook 失败: " + t.getMessage());
        }
    }

    /**
     * 方案4：Hook Dialog / AlertDialog（弹窗提示）
     */
    private void hookDialog(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> dialogClass = XposedHelpers.findClass("android.app.Dialog", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(dialogClass, "setTitle", CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            log("[DIALOG] setTitle: " + param.args[0]);
                        }
                    });
            log("✅ Dialog.setTitle Hook 已启用");
        } catch (Throwable ignored) {}

        try {
            Class<?> alertDialogClass = XposedHelpers.findClass("android.app.AlertDialog", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(alertDialogClass, "setMessage", CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            log("[ALERT] setMessage: " + param.args[0]);
                            log("[ALERT] 调用堆栈:");
                            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                                log("    at " + ste.getClassName() + "." + ste.getMethodName());
                            }
                        }
                    });
            log("✅ AlertDialog.setMessage Hook 已启用");
        } catch (Throwable ignored) {}
    }

    /**
     * 方案5：Hook 所有 Activity 的 onCreate，记录哪个 Activity 被创建
     */
    private void hookAllActivities(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(activityClass, "onCreate",
                    android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String className = param.thisObject.getClass().getName();
                            log("[ACTIVITY] 创建: " + className);
                        }
                    });
            log("✅ Activity.onCreate Hook 已启用");
        } catch (Throwable t) {
            log("❌ Activity Hook 失败: " + t.getMessage());
        }
    }
}
