package io.github.lazyimmortal.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "io.github.lazyimmortal.seedum";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("SeedumBypass: 开始注入 " + lpparam.packageName);

        // 方案1：Hook 授权检查方法 → 直接 return true
        hookAuthMethods(lpparam);

        // 方案2：拦截 SharedPreferences → after 阶段篡改返回值
        hookSharedPreferences(lpparam);

        // 方案3：拦截 Toast → after 阶段替换为空 Toast
        hookToast(lpparam);

        XposedBridge.log("SeedumBypass: 注入完成");
    }

    /**
     * 方案1：Hook 可能的授权检查方法，强制返回 true
     */
    private void hookAuthMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] authMethods = {
            "isAuthorized", "checkAuth", "verifyTelegram",
            "isTelegramVerified", "checkTelegramAuth",
            "isAuth", "hasAuth", "isValid", "checkValid",
            "verifyAuth", "isVerified", "checkLicense",
            "verifyLicense"
        };

        String[] possibleClasses = {
            "io.github.lazyimmortal.seedum.util.AuthUtil",
            "io.github.lazyimmortal.seedum.util.TelegramUtil",
            "io.github.lazyimmortal.seedum.util.ConfigUtil",
            "io.github.lazyimmortal.seedum.util.PrefUtil",
            "io.github.lazyimmortal.seedum.model.AuthModel",
            "io.github.lazyimmortal.seedum.model.UserModel",
            "io.github.lazyimmortal.seedum.model.ConfigModel",
            "io.github.lazyimmortal.seedum.MainActivity",
            "io.github.lazyimmortal.seedum.SplashActivity",
            "io.github.lazyimmortal.seedum.StartActivity",
            "io.github.lazyimmortal.seedum.LauncherActivity"
        };

        for (String className : possibleClasses) {
            try {
                Class<?> clazz = XposedHelpers.findClass(className, lpparam.classLoader);
                for (String methodName : authMethods) {
                    try {
                        XposedHelpers.findAndHookMethod(clazz, methodName,
                                XC_MethodReplacement.returnConstant(true));
                        XposedBridge.log("SeedumBypass: Hooked " + className + "." + methodName);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 方案2：用 XC_MethodHook 拦截 SharedPreferencesImpl.getBoolean/getString
     * 在 afterHookedMethod 里判断 key 名，匹配则篡改结果，否则保持原样
     */
    private void hookSharedPreferences(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> spClass = XposedHelpers.findClass(
                    "android.app.SharedPreferencesImpl", lpparam.classLoader);

            // Hook getBoolean(String key, boolean defValue)
            XposedHelpers.findAndHookMethod(spClass, "getBoolean",
                    String.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (key != null && matchKey(key)) {
                                param.setResult(true);
                                XposedBridge.log("SeedumBypass: SP[" + key + "] -> true");
                            }
                        }
                    });

            // Hook getString(String key, String defValue)
            XposedHelpers.findAndHookMethod(spClass, "getString",
                    String.class, String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (key != null && matchKey(key)) {
                                param.setResult("bypass_token_placeholder");
                                XposedBridge.log("SeedumBypass: SP[" + key + "] -> bypass_token");
                            }
                        }
                    });

            XposedBridge.log("SeedumBypass: SharedPreferences Hook 已启用");
        } catch (Throwable t) {
            XposedBridge.log("SeedumBypass: SP Hook 失败: " + t.getMessage());
        }
    }

    /**
     * 方案3：用 XC_MethodHook 拦截 Toast.makeText
     * 匹配到"未授权"等关键词就替换为空 Toast
     */
    private void hookToast(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> toastClass = XposedHelpers.findClass(
                    "android.widget.Toast", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(toastClass, "makeText",
                    android.content.Context.class,
                    CharSequence.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String text = param.args[1].toString();
                            if (text.contains("授权") || text.contains("电报")
                                    || text.contains("Telegram")
                                    || text.contains("未授权")
                                    || text.contains("unauthorized")) {
                                android.widget.Toast emptyToast =
                                        new android.widget.Toast(
                                                (android.content.Context) param.args[0]);
                                emptyToast.setDuration(0);
                                param.setResult(emptyToast);
                                XposedBridge.log("SeedumBypass: 屏蔽Toast -> " + text);
                            }
                        }
                    });
        } catch (Throwable ignored) {}
    }

    /** 判断 key 是否属于授权相关的字段名 */
    private boolean matchKey(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return lower.contains("auth")
                || lower.contains("verify")
                || lower.contains("telegram")
                || lower.contains("license")
                || lower.contains("valid")
                || lower.contains("authorized")
                || lower.contains("token");
    }
}
