package com.mei.hua;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class OtherActivity extends AppCompatActivity {

    private EditText etUserInput;
    private Button btnQuery;
    private TextView tvResultLog;
    private TextView tvCooldownHint;
    private ScrollView scrollView;
    private View btnClearLog;

    // 冷却时间控制
    private static final long COOLDOWN_MS = 5000;
    private long lastQueryTime = 0;

    // API 地址
    private static final String API_URL = "http://156.243.244.97:8888/?user=";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS) // 延长超时到10秒
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other);

        etUserInput = findViewById(R.id.et_user_input);
        btnQuery = findViewById(R.id.btn_query);
        tvResultLog = findViewById(R.id.tv_result_log);
        tvCooldownHint = findViewById(R.id.tv_cooldown_hint);
        scrollView = findViewById(R.id.scroll_view);
        btnClearLog = findViewById(R.id.btn_clear_log);

        btnQuery.setOnClickListener(v -> performQuery());
        btnClearLog.setOnClickListener(v -> clearLog());
    }

    private void performQuery() {
        String input = etUserInput.getText().toString().trim();

        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "请输入查询内容", Toast.LENGTH_SHORT).show();
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastQueryTime < COOLDOWN_MS) {
            long waitSeconds = (COOLDOWN_MS - (currentTime - lastQueryTime)) / 1000 + 1;
            Toast.makeText(this, "操作太快了，请等待 " + waitSeconds + " 秒", Toast.LENGTH_SHORT).show();
            return;
        }

        lastQueryTime = currentTime;
        showCooldownTimer();

        btnQuery.setEnabled(false);
        btnQuery.setText("查询中...");

        // 【关键修复】对中文参数进行 URL 编码
        String encodedInput = input;
        try {
            encodedInput = URLEncoder.encode(input, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }

        String url = API_URL + encodedInput;

        // 打印一下 URL 方便调试 (在 Logcat 可以看到)
        // android.util.Log.d("API_QUERY", "Request URL: " + url);

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    // 如果这里报错，通常是网络不通或者没有加 usesCleartextTraffic
                    Toast.makeText(OtherActivity.this, "请求失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    resetButton();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.body() == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(OtherActivity.this, "服务器返回为空", Toast.LENGTH_SHORT).show();
                        resetButton();
                    });
                    return;
                }

                String responseBody = response.body().string();

                runOnUiThread(() -> {
                    handleApiResponse(responseBody);
                    resetButton();
                });
            }
        });
    }

    private void handleApiResponse(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);

            int code = json.optInt("code");
            String msg = json.optString("msg");
            String username = json.optString("username");
            long uid = json.optLong("uid");

            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            String logEntry;

            if (code == 200) {
                // 成功: id：username，加上排名：uid
                logEntry = String.format("[%s]\nID：%s——排名：%s", time, username, uid);
                Toast.makeText(this, "查询成功", Toast.LENGTH_SHORT).show();
            } else if (code == 404) {
                // 失败: 未查找到用户名
                logEntry = String.format("[%s] 未查找到用户名: %s", time, username);
            } else {
                // 其他情况
                logEntry = String.format("[%s] 未知原因 (%d): %s", time, code, msg);
            }

            appendLog(logEntry + "\n---------------------------");

        } catch (Exception e) {
            e.printStackTrace();
            // 如果返回的不是标准 JSON，显示原始数据方便排查
            Toast.makeText(this, "数据解析异常", Toast.LENGTH_SHORT).show();
            appendLog("[系统] 解析错误: " + jsonStr);
        }
    }

    private void appendLog(String text) {
        String currentText = tvResultLog.getText().toString();
        if (currentText.equals("等待查询...")) {
            tvResultLog.setText(text);
        } else {
            tvResultLog.setText(currentText + "\n" + text);
        }
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void clearLog() {
        tvResultLog.setText("等待查询...");
        Toast.makeText(this, "记录已清空", Toast.LENGTH_SHORT).show();
    }

    private void resetButton() {
        btnQuery.setEnabled(true);
        btnQuery.setText("提交查询");
    }

    private void showCooldownTimer() {
        tvCooldownHint.setVisibility(View.VISIBLE);
        tvCooldownHint.setText("冷却中 (5s)");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isDestroyed()) tvCooldownHint.setVisibility(View.INVISIBLE);
        }, COOLDOWN_MS);
    }
}
