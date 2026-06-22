package io.github.lazyimmortal.bypass;

import android.content.Context;
import android.content.SharedPreferences;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "SeedumBypass";
    private static final String PACKAGE_NAME = "io.github.lazyimmortal.seedum";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(PACKAGE_NAME)) return;

        XposedBridge.log(TAG + " 模块已加载到 " + PACKAGE_NAME);

        hookSharedPreferences(lpparam);
        autoWriteAuthConfig(lpparam);
        hookTelegramBot(lpparam);
        hookNativeCheck(lpparam);
        scanAndHookAuthMethods(lpparam);
    }

    private void hookSharedPreferences(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> spClass = lpparam.classLoader.loadClass("android.app.SharedPreferencesImpl");
            XposedHelpers.findAndHookMethod(spClass, "getBoolean",
                String.class, boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if ("isActivated".equals(key) || "activated".equals(key)
                            || "authorized".equals(key) || "isAuthorized".equals(key)) {
                            boolean result = (boolean) param.getResult();
                            if (!result) {
                                param.setResult(true);
                                XposedBridge.log(TAG + " SP getBoolean(" + key + ") -> true");
                            }
                        }
                    }
                }
            );

            XposedHelpers.findAndHookMethod("android.content.ContextWrapper",
                lpparam.classLoader, "getSharedPreferences",
                String.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String name = (String) param.args[0];
                        if (name != null && (name.contains("seedum") || name.contains("module")
                            || name.contains("pref") || name.contains("config"))) {
                            SharedPreferences original = (SharedPreferences) param.getResult();
                            param.setResult(new ProxyPreferences(original));
                        }
                    }
                }
            );

            XposedBridge.log(TAG + " SharedPreferences hook 已安装");
        } catch (Exception e) {
            XposedBridge.log(TAG + " SP hook 失败: " + e.getMessage());
        }
    }

    private static class ProxyPreferences implements SharedPreferences {
        private final SharedPreferences original;

        ProxyPreferences(SharedPreferences original) { this.original = original; }

        @Override public boolean getBoolean(String key, boolean defValue) {
            if ("isActivated".equals(key) || "activated".equals(key)
                || "authorized".equals(key) || "isAuthorized".equals(key)) {
                XposedBridge.log(TAG + " Proxy getBoolean(" + key + ") -> true");
                return true;
            }
            return original.getBoolean(key, defValue);
        }
        @Override public int getInt(String key, int defValue) { return original.getInt(key, defValue); }
        @Override public long getLong(String key, long defValue) { return original.getLong(key, defValue); }
        @Override public float getFloat(String key, float defValue) { return original.getFloat(key, defValue); }
        @Override public String getString(String key, String defValue) { return original.getString(key, defValue); }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) { return original.getStringSet(key, defValues); }
        @Override public boolean contains(String key) { return original.contains(key); }
        @Override public Map<String, ?> getAll() { return original.getAll(); }
        @Override public Editor edit() {
            Editor editor = original.edit();
            editor.putBoolean("isActivated", true);
            return editor;
        }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { original.registerOnSharedPreferenceChangeListener(l); }
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) { original.unregisterOnSharedPreferenceChangeListener(l); }
    }

    private void autoWriteAuthConfig(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Application",
                lpparam.classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Context ctx = (Context) param.thisObject;

                            ctx.getSharedPreferences("io.github.lazyimmortal.seedum_preferences",
                                Context.MODE_PRIVATE).edit()
                                .putBoolean("isActivated", true)
                                .putString("activated_time", String.valueOf(System.currentTimeMillis()))
                                .putString("telegramBotToken", "bypassed")
                                .apply();

                            File dataDir = ctx.getFilesDir();
                            File[] configFiles = {
                                new File(dataDir, "config.json"),
                                new File(dataDir, "global_config.json"),
                                new File(dataDir.getParentFile(), "shared_prefs/modulePrefs.xml")
                            };
                            for (File f : configFiles) {
                                if (f.exists()) injectActivated(f);
                            }
                            XposedBridge.log(TAG + " 授权配置已写入");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + " 写入授权失败: " + e.getMessage());
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + " autoWrite hook 失败: " + e.getMessage());
        }
    }

    private void injectActivated(File file) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                line = line.replace("\"isActivated\":false", "\"isActivated\":true")
                    .replace("\"activated\":\"false\"", "\"activated\":\"true\"")
                    .replace("\"authorized\":\"false\"", "\"authorized\":\"true\"")
                    .replace("isActivated\" value=\"false\"", "isActivated\" value=\"true\"");
                sb.append(line).append("\n");
            }
            br.close();
            FileWriter fw = new FileWriter(file);
            fw.write(sb.toString());
            fw.close();
            XposedBridge.log(TAG + " 已修改: " + file.getName());
        } catch (Exception ignored) {}
    }

    private void hookTelegramBot(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> builderClass = lpparam.classLoader.loadClass("okhttp3.Request$Builder");
            XposedHelpers.findAndHookMethod(builderClass, "url", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String url = (String) param.args[0];
                        if (url != null && (url.contains("api.telegram.org")
                            || url.contains("wxpusher.zjiecode.com"))) {
                            XposedBridge.log(TAG + " 拦截 TG 请求");
                            param.args[0] = "http://127.0.0.1/blocked";
                        }
                    }
                }
            );
        } catch (Exception ignored) {}
    }

    private void hookNativeCheck(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.app.ActivityThread",
                lpparam.classLoader, "handleBindApplication",
                lpparam.classLoader.loadClass("android.app.ActivityThread$AppBindData"),
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        new Thread(() -> {
                            try { Thread.sleep(3000); scanForNativeAuth(lpparam.classLoader); } catch (Exception ignored) {}
                        }).start();
                    }
                }
            );
        } catch (Exception ignored) {}
    }

    private void scanForNativeAuth(ClassLoader cl) {
        try {
            Method getLoadedClasses = ClassLoader.class.getDeclaredMethod("getLoadedClasses");
            getLoadedClasses.setAccessible(true);
            Class<?>[] classes = (Class<?>[]) getLoadedClasses.invoke(cl);
            for (Class<?> c : classes) {
                String name = c.getName();
                if (name.startsWith("android.") || name.startsWith("java.") || name.startsWith("kotlin.")) continue;
                for (Method m : c.getDeclaredMethods()) {
                    String mn = m.getName().toLowerCase();
                    if ((mn.contains("activated") || mn.contains("auth") || mn.equals("checkuiauthnative"))
                        && m.getReturnType() == boolean.class && m.getParameterCount() <= 1) {
                        hookMethodReturnTrue(c, m.getName(), cl);
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void hookMethodReturnTrue(Class<?> clazz, String methodName, ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(clazz.getName(), cl, methodName,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.getResult() instanceof Boolean && !(boolean) param.getResult()) {
                            param.setResult(true);
                            XposedBridge.log(TAG + " 绕过: " + clazz.getSimpleName() + "." + methodName);
                        }
                    }
                }
            );
        } catch (Exception ignored) {}
    }

    private void scanAndHookAuthMethods(XC_LoadPackage.LoadPackageParam lpparam) {
        new Thread(() -> {
            try { Thread.sleep(5000); scanForNativeAuth(lpparam.classLoader); } catch (Exception ignored) {}
        }).start();
    }
}
