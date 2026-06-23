package io.github.lazyimmortal.bypass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.util.Set;

public class MainHook implements IXposedHookLoadPackage {

    // 芝麻派的包名
    private static final String TARGET_PACKAGE = "io.github.lazyimmortal.seedum";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // 只作用于芝麻派
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("✅ SeedumBypass: 开始注入 " + lpparam.packageName);

        // ===== 方案1：Hook 常见授权检查方法，强制返回 true =====
        hookAuthMethods(lpparam);

        // ===== 方案2：拦截 SharedPreferences，篡改授权标记 =====
        hookSharedPreferences(lpparam);

        // ===== 方案3：拦截 Toast，屏蔽"未授权"提示 =====
        hookToast(lpparam);

        XposedBridge.log("✅ SeedumBypass: 注入完成");
    }

    /**
     * 方案1：Hook 可能的授权检查方法
     */
    private void hookAuthMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        String[] authMethods = {
            "isAuthorized",
            "checkAuth",
            "verifyTelegram",
            "isTelegramVerified",
            "checkTelegramAuth",
            "isAuth",
            "hasAuth",
            "isValid",
            "checkValid",
            "verifyAuth",
            "isVerified",
            "checkLicense",
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
                XposedBridge.log("✅ SeedumBypass: 找到类 " + className);
                for (String methodName : authMethods) {
                    try {
                        XposedHelpers.findAndHookMethod(clazz, methodName,
                                XC_MethodReplacement.returnConstant(true));
                        XposedBridge.log("✅ Hooked: " + className + "." + methodName);
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 方案2：拦截 SharedPreferences，把所有授权相关键值对篡改成已授权
     */
    private void hookSharedPreferences(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> spClass = XposedHelpers.findClass("android.app.SharedPreferencesImpl", lpparam.classLoader);

            // 拦截 getBoolean，如果键名包含 auth/verify/telegram/license 等关键字的，返回 true
            XposedHelpers.findAndHookMethod(spClass, "getBoolean",
                    String.class, boolean.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (key != null) {
                                String lowerKey = key.toLowerCase();
                                if (lowerKey.contains("auth")
                                        || lowerKey.contains("verify")
                                        || lowerKey.contains("telegram")
                                        || lowerKey.contains("license")
                                        || lowerKey.contains("valid")
                                        || lowerKey.contains("authorized")) {
                                    XposedBridge.log("✅ SeedumBypass: 篡改 SharedPreferences " + key + " → true");
                                    return true;
                                }
                            }
                            return XposedHelpers.callOriginalMethod(param.method, param.thisObject, param.args);
                        }
                    });

            // 拦截 getString，如果值看起来是授权令牌，返回假令牌
            XposedHelpers.findAndHookMethod(spClass, "getString",
                    String.class, String.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String key = (String) param.args[0];
                            if (key != null) {
                                String lowerKey = key.toLowerCase();
                                if (lowerKey.contains("auth")
                                        || lowerKey.contains("token")
                                        || lowerKey.contains("telegram")
                                        || lowerKey.contains("license")) {
                                    XposedBridge.log("✅ SeedumBypass: 篡改 SharedPreferences " + key + " → bypass_token");
                                    return "bypass_token_placeholder";
                                }
                            }
                            return XposedHelpers.callOriginalMethod(param.method, param.thisObject, param.args);
                        }
                    });

            XposedBridge.log("✅ SeedumBypass: SharedPreferences Hook 已启用");
        } catch (Throwable t) {
            XposedBridge.log("❌ SeedumBypass: SharedPreferences Hook 失败: " + t.getMessage());
        }
    }

    /**
     * 方案3：拦截 Toast，屏蔽"未授权"提示
     */
    private void hookToast(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> toastClass = XposedHelpers.findClass("android.widget.Toast", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(toastClass, "makeText",
                    android.content.Context.class,
                    CharSequence.class,
                    int.class,
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                            String text = param.args[1].toString();
                            if (text.contains("授权") || text.contains("电报")
                                    || text.contains("Telegram") || text.contains("未授权")
                                    || text.contains("unauthorized") || text.contains("verify")) {
                                XposedBridge.log("✅ SeedumBypass: 屏蔽 Toast: " + text);
                                // 返回一个空的 Toast，不显示
                                android.widget.Toast toast = new android.widget.Toast((android.content.Context) param.args[0]);
                                toast.setDuration(0);
                                return toast;
                            }
                            return XposedHelpers.callOriginalMethod(param.method, param.thisObject, param.args);
                        }
                    });
        } catch (Throwable ignored) {}
    }
}
