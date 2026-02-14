package com.mei.hua;

// --- 所有的 Import 都在这里，确保无缺 ---
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.text.InputType;
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
import androidx.cardview.widget.CardView;
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
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

public class NewActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String PREFS_NAME = "prefs";
    private static final String DATA_URI_KEY = "data_uri";
    // private static final String KEY_SAVED_ADDRESS = "saved_base_address"; // 【移除】不再保存用户输入的基址
    private static final String PREF_ASSETS_COPIED = "assets_copied";
    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 1001;
    private static final String TARGET_PKG = "com.ztgame.bob";
    private static final int REQUEST_CODE_FLOAT_WINDOW = 2002;

    private static final int TYPE_COPY = 0;
    private static final int TYPE_EDIT = 1;
    private static final int TYPE_FLOAT = 2;

    private View layoutPermissionContainer;
    private View layoutActionContainer;
    private TextView permissionStatus;
    private GridLayout gridLayoutFunctions;

    private long exitTime = 0;
    private ActivityResultLauncher<Intent> openDirectoryLauncher;
    private List<FunctionConfig> functionConfigs = new ArrayList<>();

    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener = this::onRequestPermissionsResult;

    private void initFunctionConfigs() {
        // 1. 去蓝雾
        functionConfigs.add(new FunctionConfig("去蓝雾", "naiteaone.d",
                "files/vercache2022/android/common/data/alltextures/map1/caocongnew.unity3d_u_4",
                android.R.drawable.ic_menu_gallery, TYPE_COPY));

        // 2. 去红雾
        functionConfigs.add(new FunctionConfig("去红雾", "naiteaone.h",
                "files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4",
                android.R.drawable.ic_menu_camera, TYPE_COPY));

        // 3. 清BOB
        functionConfigs.add(new FunctionConfig("清BOB", "naiteaone.now1", "files/bob.db",
                android.R.drawable.ic_menu_delete, TYPE_COPY));

        // 4. 去皮肤
        functionConfigs.add(new FunctionConfig("去皮肤", "", "files/vercache2022/android/ver.xml",
                android.R.drawable.ic_menu_edit, TYPE_EDIT));

        // 5. 内存修改 (悬浮窗) - 自动解析锁链
        functionConfigs.add(new FunctionConfig("开启悬浮", "", "",
                android.R.drawable.ic_media_play, TYPE_FLOAT));

        for (int i = 6; i <= 12; i++) {
            functionConfigs.add(new FunctionConfig("功能 " + i, "file" + i, "path" + i, android.R.drawable.ic_menu_help, TYPE_COPY));
        }
    }

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

            generateFunctionButtons();

            findViewById(R.id.button_android_auth).setOnClickListener(this);
            findViewById(R.id.button_shizuku).setOnClickListener(this);
            findViewById(R.id.button_root).setOnClickListener(this);
            findViewById(R.id.button_delete_file).setOnClickListener(this);
            findViewById(R.id.tv_skip_auth).setOnClickListener(this);
            findViewById(R.id.tv_goto_other).setOnClickListener(this);

            copyAssetsToExternalFilesDir();
            try { Shizuku.addRequestPermissionResultListener(requestPermissionResultListener); } catch (Throwable t) {}

            openDirectoryLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            DocumentFile pickedDir = DocumentFile.fromTreeUri(this, uri);
                            if (pickedDir != null && TARGET_PKG.equals(pickedDir.getName())) {
                                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(DATA_URI_KEY, uri.toString()).apply();
                                Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show();
                                updatePermissionStatus(true);
                            } else {
                                Toast.makeText(this, "请选择: " + TARGET_PKG, Toast.LENGTH_LONG).show();
                            }
                        }
                    });

            checkHasPermission();
        } catch (Exception e) { e.printStackTrace(); }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if ((System.currentTimeMillis() - exitTime) > 2000) {
                    Toast.makeText(NewActivity.this, "再按一次退出", Toast.LENGTH_SHORT).show();
                    exitTime = System.currentTimeMillis();
                } else {
                    finishAffinity();
                    System.exit(0);
                }
            }
        });
    }

    @Override public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_android_auth) openDataDirectory();
        else if (id == R.id.button_shizuku) handleShizuku();
        else if (id == R.id.button_root) handleRoot();
        else if (id == R.id.button_delete_file) combinedDeleteFile();
        else if (id == R.id.tv_skip_auth || id == R.id.tv_goto_other) startActivity(new Intent(this, OtherActivity.class));
    }

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

            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dpToPx(10));
            shape.setColor(config.type == TYPE_FLOAT ? Color.parseColor("#009688") : Color.parseColor("#673AB7"));
            btn.setBackground(shape);

            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(50));
            btn.setLayoutParams(btnParams);

            btn.setOnClickListener(v -> {
                if (config.type == TYPE_EDIT) {
                    processXmlEdit(config);
                } else if (config.type == TYPE_FLOAT) {
                    startMemoryHackProcess(); // 【修改】直接启动内存修改流程
                } else {
                    combinedCopyAndVerifyFile(config);
                }
            });

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

    // ==================================================================================
    // [功能5] 核心逻辑：自动解析锁链 -> 校验 -> 启动
    // ==================================================================================
    private void startMemoryHackProcess() {
        if (!new RootBeer(this).isRooted()) {
            Toast.makeText(this, "需要Root权限", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            String finalAddress = null;
            String msg;

            try {
                // 1. 解析锁链，获取最终地址
                finalAddress = resolvePointerChainAndGetAddress();

                // 2. 校验最终地址的值
                int pid = getProcessID(TARGET_PKG);
                if (pid <= 0) {
                    msg = "游戏未运行";
                } else {
                    float val = readMemoryFloat(pid, finalAddress);
                    if (Math.abs(val - 1.0f) < 0.001) {
                        msg = "基址校验成功：数值为 1.0";
                    } else {
                        msg = "基址校验：数值为 " + val;
                    }
                }
            } catch (Exception e) {
                finalAddress = null; // 出现异常则地址无效
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
                    Toast.makeText(this, "无法获取有效基址，功能无法启动", Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // 【新增】解析指针锁链，获取最终内存地址
    private String resolvePointerChainAndGetAddress() throws Exception {
        int pid = getProcessID(TARGET_PKG);
        if (pid <= 0) {
            throw new Exception("游戏未运行，无法解析基址");
        }

        // 1. 查找 libunity.so 模块的基址
        long unityBase = getModuleBaseAddress(pid, "libunity.so");
        if (unityBase == 0L) {
            throw new Exception("libunity.so 模块未找到，请确保游戏已运行");
        }
        Log.d("MemoryHack", "libunity.so Base Address: 0x" + String.format("%x", unityBase));

        // 2. 计算中间指针地址: UnityBase + 0x1CC40
        long pointerAddr = unityBase + 0x1CC40L;
        Log.d("MemoryHack", "Pointer Address: 0x" + String.format("%x", pointerAddr));

        // 3. 读取该地址的 8 字节（long）值，即为 dereferenced pointer
        long dereferencedPointer = readMemoryLong(pid, pointerAddr);
        if (dereferencedPointer == -1L) {
            throw new Exception("无法读取指针值: 0x" + String.format("%x", pointerAddr));
        }
        Log.d("MemoryHack", "Dereferenced Pointer: 0x" + String.format("%x", dereferencedPointer));

        // 4. 计算最终目标地址: DereferencedPointer + 0xFC
        long finalAddress = dereferencedPointer + 0xFCL;
        Log.d("MemoryHack", "Final Target Address: 0x" + String.format("%x", finalAddress));

        return String.format("%x", finalAddress); // 返回十六进制字符串
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
        if (launchIntent != null) {
            startActivity(launchIntent);
            Toast.makeText(this, "悬浮窗已启动，游戏已打开", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "未安装球球大作战", Toast.LENGTH_SHORT).show();
        }
    }

    // --- 内存读取工具 (Shell) ---

    // 【新增】获取模块基址
    private long getModuleBaseAddress(int pid, String moduleName) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("cat /proc/" + pid + "/maps\n");
            os.writeBytes("exit\n");
            os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                // 查找包含模块名的行，并且通常基址是 r-xp (可读可执行) 的第一个地址
                if (line.contains(moduleName) && line.contains("r-xp")) {
                    String addressRange = line.substring(0, line.indexOf(" "));
                    return new BigInteger(addressRange.substring(0, addressRange.indexOf("-")), 16).longValue();
                }
            }
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0L; // Not found
    }

    // 【新增】读取 8 字节（long）内存
    private long readMemoryLong(int pid, long address) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            // 读取 8 字节
            String cmd = "dd if=/proc/" + pid + "/mem bs=1 count=8 skip=" + address + " 2>/dev/null";
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();

            InputStream in = p.getInputStream();
            byte[] buffer = new byte[8];
            int read = in.read(buffer);
            p.waitFor();

            if (read == 8) {
                return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getLong();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1L; // 错误值
    }

    private int getProcessID(String pkgName) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("pidof " + pkgName + "\n");
            os.writeBytes("exit\n");
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

    private float readMemoryFloat(int pid, String hexAddress) {
        try {
            long address = new BigInteger(hexAddress, 16).longValue();
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            String cmd = "dd if=/proc/" + pid + "/mem bs=1 count=4 skip=" + address + " 2>/dev/null";
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            InputStream in = p.getInputStream();
            byte[] buffer = new byte[4];
            int read = in.read(buffer);
            p.waitFor();
            if (read == 4) {
                return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getFloat();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return -9999f;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FLOAT_WINDOW) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                // 权限授予后，重新尝试启动内存修改流程
                startMemoryHackProcess();
            }
        }
    }

    // ... (其他原有方法: processXmlEdit, combinedCopyAndVerifyFile, etc. 保持不变) ...
    private void processXmlEdit(FunctionConfig config) {
        File filesDir = getExternalFilesDir(null);
        if (filesDir == null) return;
        String targetFullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + config.targetRelativePath;
        File localTempFile = new File(filesDir, "temp_ver_edit.xml");
        Toast.makeText(this, "处理中...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                if (!executePrivilegedCommand("cp -f \"" + targetFullPath + "\" \"" + localTempFile.getAbsolutePath() + "\"")) {
                    runOnUiThread(() -> Toast.makeText(this, "读取失败", Toast.LENGTH_SHORT).show()); return;
                }
                if (!localTempFile.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "文件未找到", Toast.LENGTH_SHORT).show()); return;
                }
                StringBuilder contentBuilder = new StringBuilder();
                boolean found = false;
                try (BufferedReader br = new BufferedReader(new FileReader(localTempFile))) {
                    String line;
                    while ((line = br.readLine()) != null) contentBuilder.append(line).append("\n");
                }
                String content = contentBuilder.toString();
                if (content.contains("shopconfig")) {
                    String newContent = content.replace("shopconfig", "");
                    try (FileWriter writer = new FileWriter(localTempFile)) { writer.write(newContent); }
                    found = true;
                }
                if (!found) {
                    runOnUiThread(() -> Toast.makeText(this, "无需修改", Toast.LENGTH_SHORT).show());
                    localTempFile.delete(); return;
                }
                if (executePrivilegedCommand("cp -f \"" + localTempFile.getAbsolutePath() + "\" \"" + targetFullPath + "\"")) {
                    runOnUiThread(() -> Toast.makeText(this, "成功！", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "写入失败", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) { e.printStackTrace(); }
            finally { if (localTempFile.exists()) localTempFile.delete(); }
        }).start();
    }

    private void combinedCopyAndVerifyFile(FunctionConfig config) {
        File filesDir = getExternalFilesDir(null);
        if (filesDir == null) return;
        File sourceFile = new File(filesDir, config.sourceAssetName);
        if (!sourceFile.exists()) { Toast.makeText(this, "本地文件缺失: " + config.sourceAssetName, Toast.LENGTH_SHORT).show(); return; }

        String destFileFullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + config.targetRelativePath;
        String destDirOnlyPath = destFileFullPath.substring(0, destFileFullPath.lastIndexOf("/"));
        File tempFile = new File(filesDir, "temp_verify_" + System.currentTimeMillis());

        Toast.makeText(this, "执行中...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                String sourceMd5 = calculateMD5(sourceFile);
                if (sourceMd5 == null) return;
                if (!executePrivilegedCommand("mkdir -p \"" + destDirOnlyPath + "\" && cp -f \"" + sourceFile.getAbsolutePath() + "\" \"" + destFileFullPath + "\"")) {
                    runOnUiThread(() -> Toast.makeText(this, "权限不足", Toast.LENGTH_SHORT).show()); return;
                }
                if (!executePrivilegedCommand("cp -f \"" + destFileFullPath + "\" \"" + tempFile.getAbsolutePath() + "\"")) {
                    runOnUiThread(() -> Toast.makeText(this, "校验失败", Toast.LENGTH_SHORT).show()); return;
                }
                String destMd5 = calculateMD5(tempFile);
                runOnUiThread(() -> {
                    if (destMd5 != null && sourceMd5.equals(destMd5)) Toast.makeText(this, "成功！", Toast.LENGTH_SHORT).show();
                    else Toast.makeText(this, "校验失败", Toast.LENGTH_SHORT).show();
                });
            } finally { if (tempFile.exists()) tempFile.delete(); }
        }).start();
    }

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
    private void combinedDeleteFile() {
        String[] targetFiles = {
                "/storage/emulated/0/Android/data/" + TARGET_PKG + "/files/vercache2022/android/common/data/alltextures/map1/caocongnew.unity3d_u_4",
                "/storage/emulated/0/Android/data/" + TARGET_PKG + "/files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4"
        };
        new Thread(() -> {
            StringBuilder cmd = new StringBuilder();
            for (String f : targetFiles) cmd.append("rm -f \"").append(f).append("\"; ");
            if (executePrivilegedCommand(cmd.toString())) runOnUiThread(() -> Toast.makeText(this, "清理完成", Toast.LENGTH_SHORT).show());
            else runOnUiThread(() -> checkSAFDelete(targetFiles));
        }).start();
    }
    private void checkSAFDelete(String[] files) { Toast.makeText(this, "请先获取授权 (SAF/Root)", Toast.LENGTH_SHORT).show(); }
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
    @Nullable private String calculateMD5(File file) { try (InputStream is = new FileInputStream(file)) { MessageDigest digest = MessageDigest.getInstance("MD5"); byte[] buffer = new byte[8192]; int read; while ((read = is.read(buffer)) > 0) digest.update(buffer, 0, read); return String.format("%32s", new BigInteger(1, digest.digest()).toString(16)).replace(' ', '0'); } catch (Exception e) { return null; } }
    private void updatePermissionStatus(boolean hasPermission) { if (hasPermission) { if (layoutPermissionContainer.getVisibility() != View.GONE) { layoutPermissionContainer.setVisibility(View.GONE); layoutActionContainer.setVisibility(View.VISIBLE); layoutActionContainer.setAlpha(0f); layoutActionContainer.animate().alpha(1f).setDuration(500).start(); } permissionStatus.setText("已授权"); permissionStatus.setTextColor(Color.GREEN); } else { layoutPermissionContainer.setVisibility(View.VISIBLE); layoutActionContainer.setVisibility(View.GONE); permissionStatus.setText("未授权"); permissionStatus.setTextColor(Color.RED); } }
    private void checkHasPermission() { boolean shizukuOk = false; try { if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) shizukuOk = true; } catch (Throwable t) {} if (shizukuOk || new RootBeer(this).isRooted()) { updatePermissionStatus(true); return; } String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null); if (uriStr != null) { try { if (DocumentFile.fromTreeUri(this, Uri.parse(uriStr)).canRead()) { updatePermissionStatus(true); return; } } catch (Exception e) {} } updatePermissionStatus(false); }
    private void copyAssetsToExternalFilesDir() { if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_ASSETS_COPIED, false)) return; File targetDir = getExternalFilesDir(null); if (targetDir == null) return; if (!targetDir.exists()) targetDir.mkdirs(); try { String[] files = getAssets().list(""); if (files != null) { for (String f : files) { try (InputStream in = getAssets().open(f); OutputStream out = new FileOutputStream(new File(targetDir, f))) { byte[] buf = new byte[1024]; int len; while ((len = in.read(buf)) != -1) out.write(buf, 0, len); } catch (IOException ignored) {} } } } catch (IOException ignored) {} getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_ASSETS_COPIED, true).apply(); }
    private void openDataDirectory() { Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Android/data")); Toast.makeText(this, "请授权 " + TARGET_PKG, Toast.LENGTH_LONG).show(); openDirectoryLauncher.launch(intent); }
    private void handleShizuku() { try { if (!Shizuku.pingBinder()) { Toast.makeText(this, "Shizuku 未运行", Toast.LENGTH_SHORT).show(); return; } if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION); } catch (Throwable t) { Toast.makeText(this, "Shizuku 异常", Toast.LENGTH_SHORT).show(); } }
    private void handleRoot() { checkHasPermission(); }
    private void onRequestPermissionsResult(int c, int r) { if (c == REQUEST_CODE_SHIZUKU_PERMISSION) checkHasPermission(); }
    @Override protected void onDestroy() { super.onDestroy(); try { Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener); } catch(Throwable t){} }
}
