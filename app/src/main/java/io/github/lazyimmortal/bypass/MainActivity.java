package io.github.lazyimmortal.bypass;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class MainActivity extends Activity {

    private static final String PREF_NAME = "bypass_status";
    private TextView statusText, statusDetail, hookInfo;
    private LinearLayout statusIndicator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIndicator = findViewById(R.id.statusIndicator);
        statusText = findViewById(R.id.statusText);
        statusDetail = findViewById(R.id.statusDetail);
        hookInfo = findViewById(R.id.hookInfo);

        Button checkBtn = findViewById(R.id.checkBtn);
        checkBtn.setOnClickListener(v -> checkBypassStatus());

        // 启动时自动检查
        checkBypassStatus();
    }

    private void checkBypassStatus() {
        boolean bypassActive = false;
        StringBuilder info = new StringBuilder();
        int hookCount = 0;

        // 1. 检查模块的 SharedPreferences（看自己是否写入过授权标记）
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        boolean selfMark = prefs.getBoolean("module_installed", false);
        if (!selfMark) {
            prefs.edit().putBoolean("module_installed", true).apply();
        }

        // 2. 检查 Seedum 的配置文件（如果已被绕过应该存在 true）
        try {
            File dataDir = new File("/data/data/io.github.lazyimmortal.seedum/");
            if (dataDir.exists()) {
                info.append("• Seedum 目录: 存在 ✓\n");

                // 检查 SharedPreferences
                File spFile = new File(dataDir, "shared_prefs/io.github.lazyimmortal.seedum_preferences.xml");
                if (spFile.exists()) {
                    String spContent = readFile(spFile);
                    if (spContent.contains("isActivated\" value=\"true\"")) {
                        bypassActive = true;
                        info.append("• isActivated: ✅ 已激活\n");
                        hookCount++;
                    } else {
                        info.append("• isActivated: ⏳ 等待 LSPosed 激活\n");
                    }
                }

                // 检查 config.json
                File configFile = new File(dataDir, "files/config.json");
                if (configFile.exists()) {
                    String config = readFile(configFile);
                    if (config.contains("\"isActivated\":true")) {
                        bypassActive = true;
                        info.append("• config.json: ✅ 已写入\n");
                        hookCount++;
                    }
                }
            } else {
                info.append("• Seedum 未安装或未运行\n");
                info.append("• 请先安装并打开一次 Seedum 应用\n");
            }
        } catch (Exception e) {
            info.append("• 检测异常: " + e.getMessage() + "\n");
        }

        // 3. 更新 UI
        if (bypassActive) {
            statusIndicator.setBackgroundResource(R.drawable.green_dot);
            statusText.setText("✅ 已激活 · 已绕过");
            statusText.setTextColor(0xFF4CAF50);
            statusDetail.setText("授权绕过已生效，Seedum 可正常使用 ⚡");
            info.append("\n共 " + hookCount + " 项绕过策略已生效");
        } else if (dataDirExists()) {
            statusIndicator.setBackgroundResource(android.R.drawable.presence_offline);
            statusText.setText("⏳ 等待 LSPosed");
            statusText.setTextColor(0xFFFFA726);
            statusDetail.setText("请在 LSPosed 中勾选此模块→重启作用域→重新打开本页面检查");
        } else {
            statusIndicator.setBackgroundResource(android.R.drawable.presence_offline);
            statusText.setText("📦 模块已安装");
            statusText.setTextColor(0xFF42A5F5);
            statusDetail.setText("模块 APK 已安装，请确保 LSPosed 中已激活并设好作用域");
        }

        hookInfo.setText(info.toString());
    }

    private boolean dataDirExists() {
        try {
            File dataDir = new File("/data/data/io.github.lazyimmortal.seedum/");
            return dataDir.exists();
        } catch (Exception e) {
            return false;
        }
    }

    private String readFile(File file) {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            br.close();
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
