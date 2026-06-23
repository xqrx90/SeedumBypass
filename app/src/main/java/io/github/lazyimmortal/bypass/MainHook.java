package io.github.lazyimmortal.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.reflect.Method;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "io.github.lazyimmortal.seedum";
    // 日志输出到手机存储根目录，用 MT 管理器直接打开
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
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            fw.write(time + " " + msg + "\n");
            fw.close();
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        // 清空旧日志（首次注入时）
        new File(LOG_FILE).delete();
        log("========== 开始注入 ==========");

        hookAllBooleanMethods(lpparam);
        hookToast(lpparam);
        hookSharedPreferences(lpparam);

        log("========== 注入完成 ==========");
    }

    /**
     * Hook 已知类的所有 boolean 方法（打日志 + 强制返回 true）
     */
    private void hookAllBooleanMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] knownClasses = {
            "io.github.lazyimmortal.seedum.util.AuthUtil",
            "io.github.lazyimmortal.seedum.util.TelegramUtil",
            "io.github.lazyimmortal.seedum.util.ConfigUtil",
            "io.github.lazyimmortal.seedum.util.PrefUtil",
            "io.github.lazyimmortal.seedum.util.ApiUtil",
            "io.github.lazyimmortal.seedum.model.AuthModel",
            "io.github.lazyimmortal.seedum.model.UserModel",
            "io.github.lazyimmortal.seedum.model.ConfigModel",
            "io.github.lazyimmortal.seedum.MainActivity",
            "io.github.lazyimmortal.seedum.SplashActivity",
            "io.github.lazyimmortal.seedum.StartActivity",
            "io.github.lazyimmortal.seedum.LauncherActivity",
            "io.github.lazyimmortal.seedum.ui.MainActivity",
            "io.github.lazyimmortal.seedum.ui.SplashActivity",
            "io.github.lazyimmortal.seedum.service.AuthService",
        };

        String[] authMethods = {
            "isAuthorized", "checkAuth", "verifyTelegram",
            "isTelegramVerified", "checkTelegramAuth",
            "isAuth", "hasAuth", "isValid", "checkValid",
            "verifyAuth", "isVerified", "checkLicense",
            "verifyLicense", "isRegistered", "checkTelegramBind",
            "getTelegramId", "hasTelegramAuth", "isTelegramAuth",
            "checkAuth", "verifyAuth", "isAuthValid"
        };

        for (String className : knownClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);

                // Hook 所有返回 boolean 的方法（打日志）
                Method[] methods = clazz.getDeclaredMethods();
                for (Method m : methods) {
                    if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                        String methodSig = className + "." + m.getName();
                        XposedHelpers.findAndHookMethod(clazz, m.getName(),
                                new XC_MethodHook() {
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        boolean result = (boolean) param.getResult();
                                        log("[BOOL] " + methodSig + "() -> " + result);
                                    }
                                });
                    }
                }
                log("已Hook类: " + className);
            } catch (Throwable ignored) {}
        }

        // 对常见授权方法强制返回 true（同时打日志）
        for (String className : knownClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                for (String methodName : authMethods) {
                    try {
                        XposedHelpers.findAndHookMethod(clazz, methodName,
                                new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        log("[CALL] " + className + "." + methodName + "() 被调用");
                                    }
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                        log("[FORCE] " + className + "." + methodName + "() -> true (强制)");
                                        param.setResult(true);
                                    }
                                });
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Hook Toast.makeText，完整记录所有提示内容
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
                            log("[TOAST] " + text);
                            // 不屏蔽，只记录
                        }
                    });
        } catch (Throwable ignored) {}
    }

    /**
     * Hook SharedPreferences，篡改授权相关键值
     */
    private void hookSharedPreferences(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> spClass = XposedHelpers.findClass(
                    "android.app.SharedPreferencesImpl", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(spClass, "getBoolean",
                    String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (key != null && matchKey(key)) {
                                boolean val = (boolean) param.getResult();
                                log("[SP_BOOL] " + key + " = " + val + " -> 强制 true");
                                param.setResult(true);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(spClass, "getString",
                    String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (key != null && matchKey(key)) {
                                log("[SP_STR] " + key + " -> 强制 bypass_token");
                                param.setResult("bypass_token_placeholder");
                            }
                        }
                    });

        } catch (Throwable t) {
            log("SP Hook 失败: " + t.getMessage());
        }
    }

    private boolean matchKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.contains("auth") || lower.contains("verify")
                || lower.contains("telegram") || lower.contains("license")
                || lower.contains("valid") || lower.contains("token")
                || lower.contains("registered") || lower.contains("bind");
    }
}
