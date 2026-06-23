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
    private static final String PKG_SEEDUM = "io.github.lazyimmortal.seedum";
    private static final String VERSION = "v6-网络拦截版";

    private static volatile String logPath = null;

    private void initLogPath(ClassLoader cl) {
        if (logPath != null) return;
        try {
            Class<?> envClass = cl.loadClass("android.os.Environment");
            File dir = (File) XposedHelpers.callStaticMethod(envClass, "getExternalStorageDirectory");
            if (dir != null) {
                File logFile = new File(dir, "SeedumBypass.log");
                logPath = logFile.getAbsolutePath();
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

        if (!PKG_SEEDUM.equals(lpparam.packageName)) {
            return;
        }

        initLogPath(lpparam.classLoader);
        log("========== SeedumBypass 注入开始 ==========");
        log("版本: " + VERSION);
        log("包名: " + lpparam.packageName);

        // ① Hook InternalSourceSet（本地检查）
        try {
            Class<?> internalCls = XposedHelpers.findClass(
                    "com.alipay.internal.InternalSourceSet",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(internalCls, "checkNative",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook checkNative() → true");

            XposedBridge.hookAllMethods(internalCls, "checkWithFactor",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook checkWithFactor() → true");

            XposedBridge.hookAllMethods(internalCls, "getCurrentResult",
                    XC_MethodReplacement.returnConstant("authorized"));
            log("✅ Hook getCurrentResult() → authorized");

            XposedBridge.hookAllMethods(internalCls, "debugMode",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook debugMode() → true");

        } catch (Throwable t) {
            log("❌ InternalSourceSet Hook 失败: " + t.getMessage());
        }

        // ② Hook WebViewLoaderImpl（UI层检查）
        try {
            Class<?> webViewCls = XposedHelpers.findClass(
                    "io.github.lazyimmortal.wrapper.internal.WebViewLoaderImpl",
                    lpparam.classLoader);

            XposedBridge.hookAllMethods(webViewCls, "checkUiAuthNative",
                    XC_MethodReplacement.returnConstant(true));
            log("✅ Hook checkUiAuthNative() → true");

        } catch (Throwable t) {
            log("❌ WebViewLoaderImpl Hook 失败: " + t.getMessage());
        }

        // ③ 🔥 网络层拦截 —— Hook JSONObject（拦截JSON解析）
        try {
            // Hook JSONObject 构造函数（从字符串构造）
            XposedHelpers.findAndHookConstructor(android.util.JsonReader.class,
                    java.io.Reader.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            // 暂时不处理，先记录
                        }
                    });
        } catch (Throwable t) {
            // 忽略
        }

        try {
            // Hook JSONObject.getString / getInt 等方法，篡改返回值
            Class<?> jsonObjCls = XposedHelpers.findClass("org.json.JSONObject", lpparam.classLoader);
            if (jsonObjCls == null) {
                // 尝试用系统类加载器
                jsonObjCls = android.util.JsonReader.class.getClassLoader().loadClass("org.json.JSONObject");
            }

            // Hook getString —— 如果返回包含"暂停"/"paused"的字符串，改成空或正常
            XposedBridge.hookAllMethods(jsonObjCls, "getString",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String result = (String) param.getResult();
                            if (result != null) {
                                String lower = result.toLowerCase();
                                if (lower.contains("暂停") || lower.contains("paused") ||
                                    lower.contains("未授权") || lower.contains("unauthorized") ||
                                    lower.contains("请联系") || lower.contains("contact")) {
                                    log("📡 JSONObject.getString 拦截: " + result);
                                    // 返回空字符串（可能让APP认为正常）
                                    param.setResult("");
                                }
                            }
                        }
                    });
            log("✅ Hook JSONObject.getString()");
        } catch (Throwable t) {
            log("⚠️ JSONObject.getString Hook 失败: " + t.getMessage());
        }

        try {
            // Hook JSONObject.put —— 拦截写入
            Class<?> jsonObjCls = XposedHelpers.findClass("org.json.JSONObject", lpparam.classLoader);
            XposedBridge.hookAllMethods(jsonObjCls, "put",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args.length >= 2) {
                                String key = param.args[0].toString();
                                Object val = param.args[1];
                                if (val != null) {
                                    String valStr = val.toString().toLowerCase();
                                    if (valStr.contains("暂停") || valStr.contains("paused") ||
                                        valStr.contains("未授权") || valStr.contains("suspended")) {
                                        log("📡 JSONObject.put 拦截: key=" + key + " val=" + val);
                                        param.args[1] = "active";
                                    }
                                }
                            }
                        }
                    });
            log("✅ Hook JSONObject.put()");
        } catch (Throwable t) {
            log("⚠️ JSONObject.put Hook 失败: " + t.getMessage());
        }

        // ④ Hook 网络请求 —— HttpURLConnection
        try {
            XposedHelpers.findAndHookMethod("libcore.io.Os", lpparam.classLoader,
                    "recvfrom", int.class, Object.class, int.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // 底层网络数据，暂不处理
                        }
                    });
        } catch (Throwable t) {
            // 忽略
        }

        // ④ Hook 可能的网络回调
        try {
            // 尝试Hook所有可能处理服务器响应的类
            // 搜索类名包含 "Http", "Net", "Api", "Server", "Response" 的类
            // 由于混淆，我们只能宽泛Hook

            // Hook byte[] 相关方法（可能是网络响应）
            XposedHelpers.findAndHookMethod(String.class, "contains",
                    CharSequence.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // 不修改，只记录
                        }
                    });
        } catch (Throwable t) {
            // 忽略
        }

        // ⑤ 拦截 Toast（最后防线）
        try {
            XposedHelpers.findAndHookMethod(android.widget.Toast.class, "makeText",
                    android.content.Context.class,
                    CharSequence.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[1] == null) return;
                            String text = param.args[1].toString();
                            if (text.contains("未授权") || text.contains("电报") ||
                                text.contains("注册") || text.contains("暂停") ||
                                text.contains("请联系") || text.contains("paused")) {
                                log("🛑 拦截 Toast: " + text);
                                param.args[1] = "授权成功";
                            }
                        }
                    });
            log("✅ Hook Toast.makeText()");
        } catch (Throwable t) {
            log("⚠️ Toast Hook 失败: " + t.getMessage());
        }

        // ⑥ 🔥 暴力方案：Hook 所有类的所有方法，找服务器响应处理
        try {
            // 在 APP 启动时，枚举所有已加载的类，Hook 所有可能处理网络响应的方法
            // 这是一个非常激进的方案，但可能有效

            // 先Hook ClassLoader.loadClass，在新类加载时检查
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass",
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.getResult() == null) return;
                            String className = param.args[0].toString();
                            // 只关注可能的网络/响应处理类
                            if (className.contains("Http") || className.contains("Net") ||
                                className.contains("Api") || className.contains("Server") ||
                                className.contains("Response") || className.contains("Request")) {
                                log("🔍 加载网络相关类: " + className);
                            }
                        }
                    });
            log("✅ Hook ClassLoader.loadClass()（用于追踪网络类）");
        } catch (Throwable t) {
            log("⚠️ ClassLoader Hook 失败: " + t.getMessage());
        }

        log("========== SeedumBypass 注入完成 ==========");
    }
}
