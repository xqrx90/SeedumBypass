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

    private void log(String msg) {
        XposedBridge.log("SeedumBypass: " + msg);
        writeFile(msg);
    }

    private void writeFile(String msg) {
        try {
            File f = new File(LOG_FILE);
            if (f.length() > 2 * 1024 * 1024) f.delete();
            FileWriter fw = new FileWriter(f, true);
            fw.write(new SimpleDateFormat("HH:mm:ss").format(new Date()) + " " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) return;

        new File(LOG_FILE).delete();
        log("========== 开始注入 ==========");

        // 核心：Hook Toast，打印完整堆栈找到授权检查位置
        hookToastStackTrace(lpparam);

        // 备用：Hook 几个最可能的关键类（即使混淆也试试）
        hookLikelyClasses(lpparam);

        log("========== 注入完成 ==========");
    }

    /**
     * Hook Toast.makeText
     * 当"账号未授权"出现时，打印【完整】调用堆栈（不过滤）
     * 堆栈里会显示是芝麻派的哪个方法调用的 Toast
     */
    private void hookToastStackTrace(XC_LoadPackage.LoadPackageParam lpparam) {
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
                            if (text.contains("授权") || text.contains("电报")
                                    || text.contains("Telegram") || text.contains("未授权")
                                    || text.contains("unauthorized") || text.contains("验证")) {

                                log("========== 🔍 发现授权提示 ==========");
                                log("[TOAST] 内容: " + text);
                                log("[STACK] 调用堆栈（从下往上看）:");

                                // 打印完整堆栈，不过滤
                                StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                                for (StackTraceElement ste : stack) {
                                    String line = "  at " + ste.getClassName()
                                            + "." + ste.getMethodName()
                                            + "(" + ste.getFileName() + ":"
                                            + ste.getLineNumber() + ")";
                                    log(line);
                                }
                                log("========== 🔍 堆栈结束 ==========");
                            }
                        }
                    });
            log("✅ Toast堆栈追踪已启用");
        } catch (Throwable t) {
            log("❌ Toast Hook 失败: " + t.getMessage());
        }
    }

    /**
     * 尝试 Hook 几个最可能的关键类
     * 即使混淆，也试试这些常见命名
     */
    private void hookLikelyClasses(XC_LoadPackage.LoadPackageParam lpparam) {
        // 混淆后的类我们猜不到，但可以尝试 Hook 所有 Activity 的 onCreate
        // 看看能不能找到授权检查
        try {
            // Hook Activity.onCreate，在 Activity 创建时打印日志
            Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(activityClass, "onCreate",
                    android.os.Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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
