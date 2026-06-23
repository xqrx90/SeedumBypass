package io.github.lazyimmortal.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "SeedumBypass";
    private static final String PKG_SEDUM = "io.github.lazyimmortal.seedum";
    private static final String VERSION = "v5-精准Hook";  // 版本标识，方便确认是否装对

    private static volatile String logPath = null;

    private void initLogPath(ClassLoader cl) {
        if (logPath != null) return;
        try {
            Class<?> envClass = cl.loadClass("android.os.Environment");
            File dir = (File) XposedHelpers.callStaticMethod(envClass, "getExternalStorageDirectory");
            if (dir != null) {
                File logFile = new File(dir, "SeedumBypass.log");
                logPath = logFile.getAbsolutePath();
                Log.i(TAG, "日志路径: " + logPath);
            }
        } catch (Throwable t) {
            logPath = "/sdcard/SeedumBypass.log";
        }
    }

    private void log(String msg) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        String line = timestamp + " " + msg;
        Log.i(TAG, line);
        if (logPath == null) return;
        try {
            FileWriter fw = new FileWriter(logPath, true);
            fw.write(line + "\n");
            fw.close();
        } catch (Exception ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!PKG_SEDUM.equals(lpparam.packageName)) {
            return;
        }

        initLogPath(lpparam.classLoader);
        log("========== SeedumBypass 注入开始 ==========");
        log("版本: " + VERSION);
        log("包名: " + lpparam.packageName);

        // ① Hook com.alipay.internal.InternalSourceSet
        try {
            Class<?> internalCls = XposedHelpers.findClass(
                    "com.alipay.internal.InternalSourceSet",
                    lpparam.classLoader);

            // checkNative() — 核心授权检查，强制返回 true
            XposedBridge.hookAllMethods(internalCls, "checkNative",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook checkNative() → 强制 true");

            // checkWithFactor() — 带因子的二次检查，强制返回 true
            XposedBridge.hookAllMethods(internalCls, "checkWithFactor",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook checkWithFactor() → 强制 true");

            // getCurrentResult() — 获取当前授权结果
            XposedBridge.hookAllMethods(internalCls, "getCurrentResult",
                    XC_MethodReplacement.returnConstant("authorized"));
            log("✅ Hook getCurrentResult() → 强制 \"authorized\"");

            // debugMode() — 开启调试模式
            XposedBridge.hookAllMethods(internalCls, "debugMode",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook debugMode() → 强制 true");

        } catch (Throwable t) {
            log("❌ InternalSourceSet Hook 失败: " + t.getMessage());
            // 打印完整堆栈方便调试
            Log.e(TAG, "InternalSourceSet error", t);
        }

        // ② Hook WebViewLoaderImpl.checkUiAuthNative
        try {
            Class<?> webViewCls = XposedHelpers.findClass(
                    "io.github.lazyimmortal.wrapper.internal.WebViewLoaderImpl",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(webViewCls, "checkUiAuthNative",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook checkUiAuthNative() → 强制 true");

        } catch (Throwable t) {
            log("❌ WebViewLoaderImpl Hook 失败: " + t.getMessage());
            Log.e(TAG, "WebViewLoaderImpl error", t);
        }

        // ③ 拦截"未授权"Toast（最后防线）
        try {
            XposedHelpers.findAndHookMethod(android.widget.Toast.class, "makeText",
                    android.content.Context.class,
                    java.lang.CharSequence.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[1] == null) return;
                            String text = param.args[1].toString();
                            if (text.contains("未授权") || text.contains("电报") || text.contains("注册")) {
                                log("🛑 拦截 Toast: " + text);
                                param.args[1] = "";
                            }
                        }
                    });
            log("✅ Hook Toast.makeText() 成功");
        } catch (Throwable t) {
            log("⚠️ Toast Hook 失败（非致命）: " + t.getMessage());
        }

        log("========== SeedumBypass 注入完成 ==========");
    }
}
