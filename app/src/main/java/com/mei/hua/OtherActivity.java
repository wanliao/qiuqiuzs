package com.mei.hua;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
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
    private static final long COOLDOWN_MS = 8000;
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

        // === 美化特效：沉浸式状态栏 ===
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); // 状态栏黑色文字
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_other);

        etUserInput = findViewById(R.id.et_user_input);
        btnQuery = findViewById(R.id.btn_query);
        tvResultLog = findViewById(R.id.tv_result_log);
        tvCooldownHint = findViewById(R.id.tv_cooldown_hint);
        scrollView = findViewById(R.id.scroll_view);
        btnClearLog = findViewById(R.id.btn_clear_log);

        btnQuery.setOnClickListener(v -> performQuery());
        btnClearLog.setOnClickListener(v -> clearLog());

        // === 美化特效：丝滑进场动画 ===
        View title = findViewById(R.id.tv_other_title);
        View subtitle = findViewById(R.id.tv_other_subtitle);
        View cardInput = findViewById(R.id.card_input_area);
        View consoleHeader = findViewById(R.id.layout_console_header);
        View consoleBody = findViewById(R.id.layout_console_body);

        View[] animViews = {title, subtitle, cardInput, consoleHeader, consoleBody};
        for (int i = 0; i < animViews.length; i++) {
            if (animViews[i] != null) {
                animViews[i].setTranslationY(80f);
                animViews[i].setAlpha(0f);
                animViews[i].animate()
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(600)
                        .setStartDelay(i * 80L) // 依次浮现，节奏感极佳
                        .setInterpolator(new DecelerateInterpolator(1.5f))
                        .start();
            }
        }
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

        // 对中文参数进行 URL 编码
        String encodedInput = input;
        try {
            encodedInput = URLEncoder.encode(input, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
        }

        String url = API_URL + encodedInput;

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
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
                // 成功
                logEntry = String.format("[%s]\nID：%s\n排名：%s", time, username, uid);
                Toast.makeText(this, "查询成功", Toast.LENGTH_SHORT).show();
            } else if (code == 404) {
                // 失败
                logEntry = String.format("[%s] 未查找到用户名: %s", time, username);
            } else {
                // 其他情况
                logEntry = String.format("[%s] 未知原因 (%d): %s", time, code, msg);
            }

            appendLog(logEntry + "\n---------------------------");

        } catch (Exception e) {
            e.printStackTrace();
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
        btnQuery.setText("立即查询");
    }

    private void showCooldownTimer() {
        tvCooldownHint.setVisibility(View.VISIBLE);
        tvCooldownHint.setText("冷却中 (8s)");
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!isDestroyed()) tvCooldownHint.setVisibility(View.INVISIBLE);
        }, COOLDOWN_MS);
    }
}