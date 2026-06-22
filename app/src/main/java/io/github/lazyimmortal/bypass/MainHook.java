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
    private static final String PKG = "io.github.lazyimmortal.seedum";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(PKG)) return;
        XposedBridge.log(TAG + " 已加载");

        // Hook SP
        try {
            XposedHelpers.findAndHookMethod("android.app.SharedPreferencesImpl",
                lpparam.classLoader, "getBoolean", String.class, boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if ("isActivated".equals(key) || "activated".equals(key) || "authorized".equals(key)) {
                            if (!(boolean) param.getResult()) {
                                param.setResult(true);
                                XposedBridge.log(TAG + " ✓ SP:" + key);
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " SP hook OK");
        } catch (Exception e) {
            XposedBridge.log(TAG + " SP hook err: " + e.getMessage());
        }

        // Write config
        try {
            XposedHelpers.findAndHookMethod("android.app.Application",
                lpparam.classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Context ctx = (Context) param.thisObject;
                            ctx.getSharedPreferences(PKG + "_preferences", Context.MODE_PRIVATE)
                                .edit().putBoolean("isActivated", true).apply();
                            XposedBridge.log(TAG + " ✓ prefs written");
                            writeConfigFile(ctx.getFilesDir(), "config.json");
                            writeConfigFile(ctx.getFilesDir(), "global_config.json");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + " write err: " + e.getMessage());
                        }
                    }
                }
            );
        } catch (Exception e) {
            XposedBridge.log(TAG + " app hook err: " + e.getMessage());
        }
    }

    private void writeConfigFile(File dir, String name) {
        try {
            File f = new File(dir, name);
            if (f.exists()) {
                String content = readFile(f);
                content = content.replace("\"isActivated\":false", "\"isActivated\":true");
                FileWriter fw = new FileWriter(f);
                fw.write(content);
                fw.close();
                XposedBridge.log(TAG + " ✓ " + name);
            }
        } catch (Exception ignored) {}
    }

    private String readFile(File f) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            br.close();
            return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
