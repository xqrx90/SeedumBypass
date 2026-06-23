package io.github.lazyimmortal.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "io.github.lazyimmortal.seedum";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("SeedumBypass: ========== 开始注入 ==========");

        // 遍历目标 APP 的所有类，Hook 所有返回 boolean 的方法
        // 同时 Hook Toast.makeText 来捕获"未授权"提示
        hookAllBooleanMethods(lpparam);
        hookToast(lpparam);
        hookSharedPreferences(lpparam);

        XposedBridge.log("SeedumBypass: ========== 注入完成 ==========");
    }

    /**
     * Hook 所有返回 boolean 的方法（只打日志，不篡改）
     * 运行一次后看 logcat，找到返回 false 的方法就是授权检查
     */
    private void hookAllBooleanMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook 所有已加载的类
            XposedBridge.log("SeedumBypass: 开始扫描类...");

            // 方案：Hook 常见类的所有方法
            // 先尝试 Hook 所有可能包含授权逻辑的类
            String[] classPrefixes = {
                "io.github.lazyimmortal.seedum"
            };

            // 由于无法直接枚举所有类，改为 Hook 几个关键类
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
                "io.github.lazyimmortal.seedum.receiver.AuthReceiver"
            };

            for (String className : knownClasses) {
                try {
                    Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                    Method[] methods = clazz.getDeclaredMethods();
                    for (Method m : methods) {
                        if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                            String methodSig = className + "." + m.getName();
                            XposedHelpers.findAndHookMethod(clazz, m.getName(),
                                    new XC_MethodHook() {
                                        @Override
                                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                            boolean result = (boolean) param.getResult();
                                            XposedBridge.log("SeedumBypass: [BOOL] " + methodSig + "() -> " + result);
                                        }
                                    });
                        }
                    }
                    XposedBridge.log("SeedumBypass: 已Hook类 " + className);
                } catch (Throwable ignored) {}
            }

            // 额外：直接返回 true 给常见授权方法（盲猜，但记录日志）
            String[] authMethods = {
                "isAuthorized", "checkAuth", "verifyTelegram",
                "isTelegramVerified", "checkTelegramAuth",
                "isAuth", "hasAuth", "isValid", "checkValid",
                "verifyAuth", "isVerified", "checkLicense",
                "verifyLicense", "isRegistered", "checkTelegramBind",
                "getTelegramId", "hasTelegramAuth", "isTelegramAuth"
            };

            for (String className : knownClasses) {
                try {
                    Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                    for (String methodName : authMethods) {
                        try {
                            XposedHelpers.findAndHookMethod(clazz, methodName,
                                    new XC_MethodHook() {
                                        @Override
                                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                            XposedBridge.log("SeedumBypass: [CALL] " + className + "." + methodName + "() 被调用");
                                        }
                                        @Override
                                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                            XposedBridge.log("SeedumBypass: [REPLACE] " + className + "." + methodName + "() -> true (强制)");
                                            param.setResult(true);
                                        }
                                    });
                            XposedBridge.log("SeedumBypass: 已Hook方法 " + className + "." + methodName);
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

        } catch (Throwable t) {
            XposedBridge.log("SeedumBypass: 扫描失败: " + t.getMessage());
        }
    }

    /**
     * Hook Toast.makeText，记录所有 Toast 内容
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
                            XposedBridge.log("SeedumBypass: [TOAST] " + text);
                            // 不屏蔽，只记录
                        }
                    });
        } catch (Throwable ignored) {}
    }

    /**
     * Hook SharedPreferences，记录所有授权相关键值对
     */
    private void hookSharedPreferences(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> spClass = XposedHelpers.findClass("android.app.SharedPreferencesImpl", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(spClass, "getBoolean",
                    String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            boolean val = (boolean) param.getResult();
                            if (matchKey(key)) {
                                XposedBridge.log("SeedumBypass: [SP_BOOL] " + key + " = " + val + " -> 强制 true");
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
                            if (matchKey(key)) {
                                XposedBridge.log("SeedumBypass: [SP_STR] " + key + " -> 强制 bypass");
                                param.setResult("bypass_token");
                            }
                        }
                    });

        } catch (Throwable t) {
            XposedBridge.log("SeedumBypass: SP Hook 失败: " + t.getMessage());
        }
    }

    private boolean matchKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.contains("auth") || lower.contains("verify")
                || lower.contains("telegram") || lower.contains("license")
                || lower.contains("valid") || lower.contains("token")
                || lower.contains("registered");
    }
}
