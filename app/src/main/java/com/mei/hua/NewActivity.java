package com.mei.hua;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import com.scottyab.rootbeer.RootBeer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public class NewActivity extends AppCompatActivity implements View.OnClickListener {

    // --- 常量配置 ---
    private static final String PREFS_NAME = "prefs";
    private static final String DATA_URI_KEY = "data_uri"; // 记录SAF目录授权的URI
    private static final String PREF_ASSETS_COPIED = "assets_copied";
    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 1001;
    private static final int REQUEST_CODE_FLOAT_WINDOW = 2002;
    private static final String TARGET_PKG = "com.ztgame.bob"; // 目标游戏包名

    // 功能类型枚举常量
    private static final int TYPE_COPY = 0;       // 普通文件替换 (去雾等)
    private static final int TYPE_FLOAT = 1;      // 悬浮窗内存修改
    private static final int TYPE_SKIN = 2;       // 去皮肤 (修改 XML)
    private static final int TYPE_ACTIVITY = 3;   // 去活动 (修改 XML)
    private static final int TYPE_FUNC6 = 4;      // 预留功能 6

    // UI 组件
    private View layoutPermissionContainer;
    private View layoutActionContainer;
    private TextView permissionStatus;
    private GridLayout gridLayoutFunctions;

    private long exitTime = 0;
    private ActivityResultLauncher<Intent> openDirectoryLauncher;
    private List<FunctionConfig> functionConfigs = new ArrayList<>();

    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener = this::onRequestPermissionsResult;

    // ==========================================
    // 1. 初始化功能配置列表
    // ==========================================
    private void initFunctionConfigs() {
        // 普通文件替换
        functionConfigs.add(new FunctionConfig("去蓝雾", "naiteaone.d", "files/vercache2022/android/common/data/alltextures/map1/caocongnew.unity3d_u_4", android.R.drawable.ic_menu_gallery, TYPE_COPY));
        functionConfigs.add(new FunctionConfig("去红雾", "naiteaone.h", "files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4", android.R.drawable.ic_menu_camera, TYPE_COPY));
        functionConfigs.add(new FunctionConfig("清BOB", "naiteaone.now1", "files/bob.db", android.R.drawable.ic_menu_delete, TYPE_COPY));

        // 特殊修改功能
        functionConfigs.add(new FunctionConfig("去皮肤", "", "files/vercache2022/android/ver.xml", android.R.drawable.ic_menu_edit, TYPE_SKIN));
        functionConfigs.add(new FunctionConfig("去活动", "", "", android.R.drawable.ic_menu_edit, TYPE_ACTIVITY));

        // 悬浮窗
        functionConfigs.add(new FunctionConfig("开启悬浮", "", "", android.R.drawable.ic_media_play, TYPE_FLOAT));

        // 预留功能
        functionConfigs.add(new FunctionConfig("功能 6", "", "", android.R.drawable.ic_menu_help, TYPE_FUNC6));

        // 凑齐 12 个格子
        for (int i = 8; i <= 12; i++) {
            functionConfigs.add(new FunctionConfig("功能 " + i, "file" + i, "path" + i, android.R.drawable.ic_menu_help, TYPE_COPY));
        }
    }

    // 内部类：用于存放每个功能的配置信息
    static class FunctionConfig {
        String btnName; String sourceAssetName; String targetRelativePath; int imageResId; int type;
        public FunctionConfig(String name, String src, String dest, int img, int type) {
            this.btnName = name; this.sourceAssetName = src; this.targetRelativePath = dest; this.imageResId = img; this.type = type;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new);

        try {
            initFunctionConfigs();
            layoutPermissionContainer = findViewById(R.id.layout_permission_container);
            layoutActionContainer = findViewById(R.id.layout_action_container);
            permissionStatus = findViewById(R.id.permission_status);
            gridLayoutFunctions = findViewById(R.id.grid_layout_functions);

            if (gridLayoutFunctions == null) return;

            // 动态生成界面按钮
            generateFunctionButtons();

            // 绑定基础授权按钮点击事件
            findViewById(R.id.button_android_auth).setOnClickListener(this);
            findViewById(R.id.button_shizuku).setOnClickListener(this);
            findViewById(R.id.button_root).setOnClickListener(this);
            findViewById(R.id.button_delete_file).setOnClickListener(this);
            findViewById(R.id.tv_skip_auth).setOnClickListener(this);
            findViewById(R.id.tv_goto_other).setOnClickListener(this);

            // 释放 Assets 文件到本地
            copyAssetsToExternalFilesDir();

            try { Shizuku.addRequestPermissionResultListener(requestPermissionResultListener); } catch (Throwable t) {}

            // SAF 授权回调
            openDirectoryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
                            if (pickedDir != null && TARGET_PKG.equals(pickedDir.getName())) {
                                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(DATA_URI_KEY, uri.toString()).apply();
                                Toast.makeText(this, "安卓 Data 目录授权成功", Toast.LENGTH_SHORT).show();
                                updatePermissionStatus(true);
                            } else {
                                Toast.makeText(this, "授权失败：请务必选择包名为 " + TARGET_PKG + " 的文件夹", Toast.LENGTH_LONG).show();
                            }
                        }
                    });

            checkHasPermission();

        } catch (Exception e) { e.printStackTrace(); }

        // 拦截返回键，实现双击退出
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if ((System.currentTimeMillis() - exitTime) > 2000) {
                    Toast.makeText(NewActivity.this, "再按一次退出助手", Toast.LENGTH_SHORT).show();
                    exitTime = System.currentTimeMillis();
                } else {
                    finishAffinity();
                    System.exit(0);
                }
            }
        });
    }

    // 处理顶部和底部的固定按钮点击事件
    @Override public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_android_auth) openDataDirectory();
        else if (id == R.id.button_shizuku) handleShizuku();
        else if (id == R.id.button_root) handleRoot();
        else if (id == R.id.button_delete_file) combinedDeleteFile();
        else if (id == R.id.tv_skip_auth || id == R.id.tv_goto_other) startActivity(new Intent(this, OtherActivity.class));
    }

    // ==========================================
    // 2. 动态生成网格按钮及事件分发
    // ==========================================
    private void generateFunctionButtons() {
        gridLayoutFunctions.removeAllViews();
        for (int i = 0; i < functionConfigs.size(); i++) {
            FunctionConfig config = functionConfigs.get(i);
            FrameLayout container = new FrameLayout(this);
            GridLayout.LayoutParams gridParams = new GridLayout.LayoutParams();
            gridParams.width = 0;
            gridParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            int margin = dpToPx(6);
            gridParams.setMargins(margin, margin, margin, margin);
            gridParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            container.setLayoutParams(gridParams);

            Button btn = new Button(this);
            btn.setText(config.btnName);
            btn.setTextSize(11);
            btn.setTextColor(Color.WHITE);
            btn.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            btn.setPadding(dpToPx(8), 0, dpToPx(30), 0);

            // 设置按钮背景圆角和颜色
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dpToPx(10));
            shape.setColor(config.type == TYPE_FLOAT ? Color.parseColor("#009688") : Color.parseColor("#673AB7"));
            btn.setBackground(shape);

            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(50));
            btn.setLayoutParams(btnParams);

            // 【事件分发核心】根据配置类型调用不同方法
            btn.setOnClickListener(v -> {
                switch (config.type) {
                    case TYPE_SKIN:
                        processSkinRemove();
                        break;
                    case TYPE_ACTIVITY:
                        processActivityRemove();
                        break;
                    case TYPE_FLOAT:
                        startMemoryHackProcess();
                        break;
                    case TYPE_FUNC6:
                        processFunction6();
                        break;
                    case TYPE_COPY:
                    default:
                        combinedCopyAndVerifyFile(config); // 通用文件替换
                        break;
                }
            });

            // 信息提示小图标
            ImageView iconInfo = new ImageView(this);
            iconInfo.setImageResource(android.R.drawable.ic_menu_info_details);
            iconInfo.setColorFilter(Color.parseColor("#FF9800"));
            iconInfo.setBackgroundColor(Color.WHITE);
            GradientDrawable iconShape = new GradientDrawable();
            iconShape.setShape(GradientDrawable.OVAL);
            iconShape.setColor(Color.WHITE);
            iconInfo.setBackground(iconShape);
            int padding = dpToPx(2);
            iconInfo.setPadding(padding, padding, padding, padding);
            int iconSize = dpToPx(16);
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            iconParams.setMargins(0, 0, dpToPx(6), 0);
            iconInfo.setLayoutParams(iconParams);
            iconInfo.setElevation(dpToPx(5));
            iconInfo.setOnClickListener(v -> showImagePopup(config));

            container.addView(btn);
            container.addView(iconInfo);
            gridLayoutFunctions.addView(container);
        }
    }

    // ==========================================
    // 3. 业务功能实现区 (修改XML / 文件替换)
    // ==========================================

    // 功能：去皮肤
    private void processSkinRemove() {
        Toast.makeText(this, "正在执行去皮肤...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File filesDir = getExternalFilesDir(null);

                // 1. 推送 shopconfig 文件到游戏 data 目录
                String assetFileName = "shopconfig.unity3d_u_4a8cff7be4e75aac023cef580ff68a15";
                File localAssetFile = new File(filesDir, assetFileName);
                String targetRelativePath = "files/vercache2022/android/common/data/" + assetFileName;

                if (!localAssetFile.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "本地资源丢失，请重装软件", Toast.LENGTH_SHORT).show());
                    return;
                }

                if (!copyToAndroidData(localAssetFile, targetRelativePath)) {
                    runOnUiThread(() -> Toast.makeText(this, "写入皮肤文件失败，请检查授权", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 2. 将游戏的 ver.xml 拷贝到本地临时目录进行修改
                String xmlRelativePath = "files/vercache2022/android/ver.xml";
                File localTempXml = new File(filesDir, "temp_ver_edit.xml");

                if (!copyFromAndroidData(xmlRelativePath, localTempXml)) {
                    runOnUiThread(() -> Toast.makeText(this, "读取 ver.xml 失败", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 3. 正则匹配并修改 MD5
                StringBuilder contentBuilder = new StringBuilder();
                boolean found = false;
                try (BufferedReader br = new BufferedReader(new FileReader(localTempXml))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // 寻找对应节点，替换其双引号内的 MD5 值
                        if (line.contains("shopconfig.unity3d")) {
                            line = line.replaceAll("md5=\"[^\"]*\"", "md5=\"4a8cff7be4e75aac023cef580ff68a15\"");
                            found = true;
                        }
                        contentBuilder.append(line).append("\n");
                    }
                }

                if (!found) {
                    runOnUiThread(() -> Toast.makeText(this, "ver.xml 中未找到皮肤配置", Toast.LENGTH_SHORT).show());
                    localTempXml.delete();
                    return;
                }

                try (FileWriter writer = new FileWriter(localTempXml)) {
                    writer.write(contentBuilder.toString());
                }

                // 4. 将修改后的 xml 推送回游戏目录覆盖
                if (copyToAndroidData(localTempXml, xmlRelativePath)) {
                    runOnUiThread(() -> Toast.makeText(this, "去皮肤成功！", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "覆盖 ver.xml 失败", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                File temp = new File(getExternalFilesDir(null), "temp_ver_edit.xml");
                if (temp.exists()) temp.delete(); // 清理临时文件
            }
        }).start();
    }

    // 功能：去活动
    private void processActivityRemove() {
        Toast.makeText(this, "正在执行去活动...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File filesDir = getExternalFilesDir(null);

                // 1. 推送 activityconfig 文件
                String assetFileName = "activityconfig.unity3d_u_30c0aa8a66fbece47834003b8e3d3bf7";
                File localAssetFile = new File(filesDir, assetFileName);
                String targetRelativePath = "files/vercache2022/android/common/data/" + assetFileName;

                if (!localAssetFile.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "本地资源丢失，请重装软件", Toast.LENGTH_SHORT).show());
                    return;
                }

                if (!copyToAndroidData(localAssetFile, targetRelativePath)) {
                    runOnUiThread(() -> Toast.makeText(this, "写入活动文件失败，请检查授权", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 2. 拉取 ver.xml
                String xmlRelativePath = "files/vercache2022/android/ver.xml";
                File localTempXml = new File(filesDir, "temp_ver_edit.xml");

                if (!copyFromAndroidData(xmlRelativePath, localTempXml)) {
                    runOnUiThread(() -> Toast.makeText(this, "读取 ver.xml 失败", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 3. 修改 xml
                StringBuilder contentBuilder = new StringBuilder();
                boolean found = false;
                try (BufferedReader br = new BufferedReader(new FileReader(localTempXml))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.contains("activityconfig.unity3d")) {
                            line = line.replaceAll("md5=\"[^\"]*\"", "md5=\"30c0aa8a66fbece47834003b8e3d3bf7\"");
                            found = true;
                        }
                        contentBuilder.append(line).append("\n");
                    }
                }

                if (!found) {
                    runOnUiThread(() -> Toast.makeText(this, "ver.xml 中未找到活动配置", Toast.LENGTH_SHORT).show());
                    localTempXml.delete();
                    return;
                }

                try (FileWriter writer = new FileWriter(localTempXml)) {
                    writer.write(contentBuilder.toString());
                }

                // 4. 覆盖回原文件
                if (copyToAndroidData(localTempXml, xmlRelativePath)) {
                    runOnUiThread(() -> Toast.makeText(this, "去活动成功！", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "覆盖 ver.xml 失败", Toast.LENGTH_SHORT).show());
                }

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } finally {
                File temp = new File(getExternalFilesDir(null), "temp_ver_edit.xml");
                if (temp.exists()) temp.delete();
            }
        }).start();
    }

    // 功能 6 (预留位)
    private void processFunction6() {
        Toast.makeText(this, "功能 6 已就绪，等待编写逻辑...", Toast.LENGTH_SHORT).show();
    }

    // 通用功能：普通文件替换 (去蓝雾、清BOB等)
    private void combinedCopyAndVerifyFile(FunctionConfig config) {
        Toast.makeText(this, "正在处理: " + config.btnName, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File localFile = new File(getExternalFilesDir(null), config.sourceAssetName);
            if (!localFile.exists()) {
                runOnUiThread(() -> Toast.makeText(this, "本地文件缺失: " + config.sourceAssetName, Toast.LENGTH_SHORT).show());
                return;
            }

            // 直接调用我们的通用 API 进行写入
            if (copyToAndroidData(localFile, config.targetRelativePath)) {
                runOnUiThread(() -> Toast.makeText(this, config.btnName + " 成功！", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(this, config.btnName + " 失败，请检查授权状态", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 清理功能：一键还原 (通过通用 API 删除文件)
    private void combinedDeleteFile() {
        Toast.makeText(this, "正在清理还原...", Toast.LENGTH_SHORT).show();
        String[] targetFiles = {
                "files/vercache2022/android/common/data/alltextures/map1/caocongnew.unity3d_u_4",
                "files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4",
                "files/vercache2022/android/ver.xml"
        };
        new Thread(() -> {
            boolean allSuccess = true;
            for (String relativePath : targetFiles) {
                if (!deleteFromAndroidData(relativePath)) {
                    allSuccess = false;
                }
            }
            boolean finalAllSuccess = allSuccess;
            runOnUiThread(() -> {
                if (finalAllSuccess) Toast.makeText(this, "清理还原完成", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, "部分清理失败，请重试", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ==========================================
    // 4. 内存解析与悬浮窗区
    // ==========================================
    private void startMemoryHackProcess() {
        if (!new RootBeer(this).isRooted()) {
            Toast.makeText(this, "内存修改功能仅支持 Root 权限", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            String finalAddress = null;
            String msg;

            try {
                // 1. 解析锁链，获取最终地址
                finalAddress = resolvePointerChainAndGetAddress();

                // 2. 校验最终地址的值，并提示用户
                int pid = getProcessID(TARGET_PKG);
                if (pid <= 0) {
                    msg = "游戏未运行，请先打开游戏";
                } else {
                    float val = readMemoryFloat(pid, finalAddress);
                    if (Math.abs(val - 1.0f) < 0.001) {
                        msg = "解析成功！\n读取浮点值: " + val + "\n(校验通过，符合1.0，锁链正常)";
                    } else {
                        msg = "解析完毕！\n当前读取浮点值: " + val + "\n(警告：数值未匹配 1.0)";
                    }
                }
            } catch (Exception e) {
                finalAddress = null;
                msg = "解析失败: " + e.getMessage();
                Log.e("MemoryHack", "Pointer chain resolution failed", e);
            }

            String finalMsg = msg;
            String finalHexAddress = finalAddress;

            runOnUiThread(() -> {
                Toast.makeText(this, finalMsg, Toast.LENGTH_LONG).show();
                if (finalHexAddress != null) {
                    startFloatWindowAndGame(finalHexAddress);
                } else {
                    Toast.makeText(this, "无法获取有效基址，悬浮窗启动中止", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // 解析指针锁链获取最终地址
    private String resolvePointerChainAndGetAddress() throws Exception {
        int pid = getProcessID(TARGET_PKG);
        if (pid <= 0) throw new Exception("游戏未运行");

        // 1. 获取 libunity.so 后面的第一个 .bss 数据段基址
        long unityBase = getModuleBaseAddress(pid, "libunity.so");
        if (unityBase == 0L) throw new Exception("未找到 libunity.so 的 .bss 段基址");

        // 2. 偏移 0x1CC40 获取指针
        long pointerAddr = unityBase + 0x1CC40L;
        long dereferencedPointer = readMemoryLong(pid, pointerAddr);
        if (dereferencedPointer <= 0L) throw new Exception("无法读取指针值: 0x" + String.format("%x", pointerAddr));

        // 3. 最终地址
        long finalAddress = dereferencedPointer + 0xFCL;
        return String.format("%x", finalAddress);
    }

    private void startFloatWindowAndGame(String hexAddress) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_FLOAT_WINDOW);
            return;
        }

        Intent serviceIntent = new Intent(this, FloatWindowService.class);
        serviceIntent.putExtra("BASE_ADDRESS", hexAddress);
        startService(serviceIntent);

        PackageManager packageManager = getPackageManager();
        Intent launchIntent = packageManager.getLaunchIntentForPackage(TARGET_PKG);
        if (launchIntent != null) startActivity(launchIntent);
    }

    // --- 内存工具类 (Shell 实现) ---
    private int getProcessID(String pkgName) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("pidof " + pkgName + "\nexit\n");
            os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) {
                String[] pids = line.trim().split("\\s+");
                return Integer.parseInt(pids[0]);
            }
        } catch (Exception e) {}
        return -1;
    }

    private long getModuleBaseAddress(int pid, String moduleName) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("cat /proc/" + pid + "/maps\nexit\n");
            os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean foundSo = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains(moduleName)) foundSo = true;
                // 找到模块后，向下寻找第一个 [anon:.bss] 段
                if (foundSo && line.contains("[anon:.bss]")) {
                    String addressRange = line.substring(0, line.indexOf(" "));
                    return new BigInteger(addressRange.substring(0, addressRange.indexOf("-")), 16).longValue();
                }
            }
            p.waitFor();
        } catch (Exception e) { e.printStackTrace(); }
        return 0L;
    }

    private long readMemoryLong(int pid, long address) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd if=/proc/" + pid + "/mem bs=1 count=8 skip=" + address + " 2>/dev/null"});
            InputStream in = p.getInputStream();
            byte[] buffer = new byte[8];
            int read = 0;
            while (read < 8) {
                int r = in.read(buffer, read, 8 - read);
                if (r == -1) break;
                read += r;
            }
            p.waitFor();
            if (read == 8) return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (Exception e) { e.printStackTrace(); }
        return -1L;
    }

    private float readMemoryFloat(int pid, String hexAddress) {
        try {
            long address = new BigInteger(hexAddress, 16).longValue();
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd if=/proc/" + pid + "/mem bs=1 count=4 skip=" + address + " 2>/dev/null"});
            InputStream in = p.getInputStream();
            byte[] buffer = new byte[4];
            int read = 0;
            while (read < 4) {
                int r = in.read(buffer, read, 4 - read);
                if (r == -1) break;
                read += r;
            }
            p.waitFor();
            if (read == 4) return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getFloat();
        } catch (Exception e) { e.printStackTrace(); }
        return -9999f;
    }

    // ==========================================
    // 5. 终极文件读写 API (自动适配 Root/Shizuku/SAF)
    // ==========================================

    /**
     * 将本地文件推送到游戏的 Data 目录中
     * @param localFile 本地源文件
     * @param targetRelativePath 目标相对路径 (如: files/...)
     */
    private boolean copyToAndroidData(File localFile, String targetRelativePath) {
        // 第一顺位：利用 Root 或 Shizuku 直接覆盖
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + targetRelativePath;
        String dirOnly = fullPath.substring(0, fullPath.lastIndexOf("/"));
        if (executePrivilegedCommand("mkdir -p \"" + dirOnly + "\" && cp -f \"" + localFile.getAbsolutePath() + "\" \"" + fullPath + "\"")) {
            return true;
        }

        // 第二顺位：降级使用 SAF 授权流写入 (兼容无Root的安卓10/11+)
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                DocumentFile targetDoc = getDocumentFileSafely(targetRelativePath, true, false);
                if (targetDoc != null) {
                    OutputStream out = getContentResolver().openOutputStream(targetDoc.getUri(), "wt"); // wt: 清空重写
                    InputStream in = new FileInputStream(localFile);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    in.close();
                    if (out != null) out.close();
                    return true;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return false;
    }

    /**
     * 从游戏的 Data 目录中拉取文件到本地
     */
    private boolean copyFromAndroidData(String sourceRelativePath, File localDest) {
        // 优先使用命令拷贝
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + sourceRelativePath;
        if (executePrivilegedCommand("cp -f \"" + fullPath + "\" \"" + localDest.getAbsolutePath() + "\"")) {
            return true;
        }

        // 降级使用 SAF 流读取
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                DocumentFile sourceDoc = getDocumentFileSafely(sourceRelativePath, false, false);
                if (sourceDoc != null && sourceDoc.exists()) {
                    InputStream in = getContentResolver().openInputStream(sourceDoc.getUri());
                    OutputStream out = new FileOutputStream(localDest);
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    if (in != null) in.close();
                    out.close();
                    return true;
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return false;
    }

    /**
     * 从游戏的 Data 目录中删除指定文件
     */
    private boolean deleteFromAndroidData(String relativePath) {
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + relativePath;
        if (executePrivilegedCommand("rm -f \"" + fullPath + "\"")) return true;

        try {
            DocumentFile target = getDocumentFileSafely(relativePath, false, false);
            if (target != null && target.exists()) return target.delete();
        } catch(Exception e) {}
        return false;
    }

    /**
     * SAF 目录解析引擎：利用 URI 逐层深入寻找文件，支持自动创建缺失文件夹
     */
    private DocumentFile getDocumentFileSafely(String relativePath, boolean createIfMissing, boolean isDirectory) {
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr == null) return null;

        DocumentFile current = DocumentFile.fromTreeUri(this, Uri.parse(uriStr));
        if (current == null) return null;

        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;

            DocumentFile next = current.findFile(part);
            if (next == null) {
                if (createIfMissing) {
                    if (i == parts.length - 1 && !isDirectory) {
                        next = current.createFile("*/*", part); // 最后一层创建文件
                    } else {
                        next = current.createDirectory(part);   // 路径中途创建文件夹
                    }
                } else {
                    return null;
                }
            }
            current = next;
        }
        return current;
    }

    // ==========================================
    // 6. 系统辅助与权限管理
    // ==========================================
    private boolean executePrivilegedCommand(String command) {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Process p = null;
                try {
                    Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                    m.setAccessible(true);
                    p = (Process) m.invoke(null, new Object[]{new String[]{"sh", "-c", command}, null, null});
                    return p.waitFor() == 0;
                } catch (Exception e) {} finally { if (p != null) p.destroy(); }
            }
        } catch (Throwable t) {}

        if (new RootBeer(this).isRooted()) {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes(command + "\nexit\n");
                os.flush();
                return p.waitFor() == 0;
            } catch (Exception e) {}
        }
        return false;
    }

    private void updatePermissionStatus(boolean hasPermission) {
        if (hasPermission) {
            if (layoutPermissionContainer.getVisibility() != View.GONE) {
                layoutPermissionContainer.setVisibility(View.GONE);
                layoutActionContainer.setVisibility(View.VISIBLE);
                layoutActionContainer.setAlpha(0f);
                layoutActionContainer.animate().alpha(1f).setDuration(500).start();
            }
            permissionStatus.setText("已授权");
            permissionStatus.setTextColor(Color.GREEN);
        } else {
            layoutPermissionContainer.setVisibility(View.VISIBLE);
            layoutActionContainer.setVisibility(View.GONE);
            permissionStatus.setText("未授权");
            permissionStatus.setTextColor(Color.RED);
        }
    }

    private void checkHasPermission() {
        boolean shizukuOk = false;
        try { if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) shizukuOk = true; } catch (Throwable t) {}
        if (shizukuOk || new RootBeer(this).isRooted()) {
            updatePermissionStatus(true);
            return;
        }
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                if (DocumentFile.fromTreeUri(this, Uri.parse(uriStr)).canRead()) {
                    updatePermissionStatus(true);
                    return;
                }
            } catch (Exception e) {}
        }
        updatePermissionStatus(false);
    }

    private void copyAssetsToExternalFilesDir() {
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_ASSETS_COPIED, false)) return;
        File targetDir = getExternalFilesDir(null);
        if (targetDir == null) return;
        if (!targetDir.exists()) targetDir.mkdirs();
        try {
            String[] files = getAssets().list("");
            if (files != null) {
                for (String f : files) {
                    try (InputStream in = getAssets().open(f); OutputStream out = new FileOutputStream(new File(targetDir, f))) {
                        byte[] buf = new byte[1024]; int len; while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                    } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_ASSETS_COPIED, true).apply();
    }

    private void openDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Android/data"));
        Toast.makeText(this, "请授权 " + TARGET_PKG + " 目录", Toast.LENGTH_LONG).show();
        openDirectoryLauncher.launch(intent);
    }

    private void handleShizuku() {
        try {
            if (!Shizuku.pingBinder()) { Toast.makeText(this, "Shizuku 未运行", Toast.LENGTH_SHORT).show(); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
        } catch (Throwable t) { Toast.makeText(this, "Shizuku 异常", Toast.LENGTH_SHORT).show(); }
    }

    private void handleRoot() { checkHasPermission(); }

    private void onRequestPermissionsResult(int c, int r) { if (c == REQUEST_CODE_SHIZUKU_PERMISSION) checkHasPermission(); }

    private int dpToPx(int dp) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()); }

    private void showImagePopup(FunctionConfig config) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        ImageView imageView = new ImageView(this);
        imageView.setImageResource(config.imageResId);
        imageView.setAdjustViewBounds(true);
        imageView.setPadding(20, 20, 20, 20);
        imageView.setBackgroundColor(Color.WHITE);
        builder.setView(imageView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FLOAT_WINDOW) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) startMemoryHackProcess();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        try { Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener); } catch(Throwable t){}
    }
}