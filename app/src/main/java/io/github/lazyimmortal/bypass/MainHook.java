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
        XposedBridge.log(TAG + " loaded");

        hookSharedPreferences(lpparam);
        hookApplication(lpparam);
    }

    private void hookSharedPreferences(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.app.SharedPreferencesImpl",
                lpparam.classLoader, "getBoolean", String.class, boolean.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String key = (String) param.args[0];
                        if ("isActivated".equals(key) || "activated".equals(key)
                            || "authorized".equals(key) || "isAuthorized".equals(key)) {
                            if (!(boolean) param.getResult()) {
                                param.setResult(true);
                                XposedBridge.log(TAG + " SP " + key + " -> true");
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " SP hook OK");
        } catch (Exception e) {
            XposedBridge.log(TAG + " SP err: " + e.getMessage());
        }
    }

    private void hookApplication(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("android.app.Application",
                lpparam.classLoader, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context ctx = (Context) param.thisObject;
                        try {
                            ctx.getSharedPreferences(PKG + "_preferences",
                                Context.MODE_PRIVATE).edit()
                                .putBoolean("isActivated", true).apply();
                            XposedBridge.log(TAG + " prefs written");
                        } catch (Exception e) {
                            XposedBridge.log(TAG + " prefs err: " + e.getMessage());
                        }

                        try {
                            File f = new File(ctx.getFilesDir(), "config.json");
                            if (f.exists()) {
                                String s = read(f);
                                s = s.replace("\"isActivated\":false", "\"isActivated\":true")
                                     .replace("\"activated\":\"false\"", "\"activated\":\"true\"");
                                FileWriter fw = new FileWriter(f);
                                fw.write(s); fw.close();
                                XposedBridge.log(TAG + " config.json patched");
                            }
                        } catch (Exception ignored) {}
                    }
                }
            );
            XposedBridge.log(TAG + " App hook OK");
        } catch (Exception e) {
            XposedBridge.log(TAG + " App err: " + e.getMessage());
        }
    }

    private String read(File f) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f)));
            StringBuilder sb = new StringBuilder(); String l;
            while ((l = br.readLine()) != null) sb.append(l).append("\n");
            br.close(); return sb.toString();
        } catch (Exception e) { return ""; }
    }
}
