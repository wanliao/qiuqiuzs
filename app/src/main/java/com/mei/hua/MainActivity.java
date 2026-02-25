package com.mei.hua;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.GregorianCalendar;

public class MainActivity extends AppCompatActivity {

    // 配置信息
    String WEIURL = "https://wy.llua.cn";
    String WEIAID = "64774";
    String WEIKEY = "w6bf22ad03ec445293fa400d9b7cb25";
    String RC4KEY = "pcec1bd6e6de1083215cecda4ae5284";
    String DLCODE = "200";
    String EDITION = "1.0";

    // 免责声明文本
    private static final String DISCLAIMER_TEXT =
            "1. 软件性质与目的\n\n" +
                    "本软件（以下简称“本工具”）仅供个人学习、技术研究及交流之用。用户在使用本工具前，应确保其行为符合当地相关法律法规及所涉及游戏/软件的服务协议。\n\n" +
                    "2. 风险提示（特别强调）\n\n" +
                    "账号安全： 使用第三方辅助工具可能被游戏官方检测为违规行为。由此导致的封号、禁言、数据删除或财产损失，由用户自行承担。\n\n" +
                    "系统安全： 本工具不保证与所有操作系统完全兼容，亦不保证不会与其他软件产生冲突。因使用本工具导致的任何系统崩溃或数据丢失，本开发者不承担任何责任。\n\n" +
                    "3. 责任界定\n\n" +
                    "无担保： 本工具按“现状”提供，不附带任何形式的明示或暗示保证，包括但不限于对特定用途的适用性或无侵权性的保证。\n\n" +
                    "非法用途禁止： 严禁将本工具用于任何非法牟利、破坏公平竞争环境或侵犯他人合法权益的行为。\n\n" +
                    "第三方损失： 本开发者不对因用户使用本工具而对任何第三方造成的直接或间接损失负责。\n\n" +
                    "4. 知识产权与分发\n\n" +
                    "本工具涉及的所有权和知识产权归原作者所有。\n" +
                    "未经许可，任何个人或组织不得将本工具用于商业售卖。如因非法二次分发、修改或包装本工具而产生的纠纷，由分发者承担全部责任。\n\n" +
                    "5. 声明的接受\n\n" +
                    "一旦您开始下载、安装或使用本工具，即视为您已充分阅读、理解并同意本声明的所有条款。 如果您不同意本声明中的任何内容，请立即停止使用并删除相关文件。";

    EditText kami;
    CheckBox cbAgree; // 勾选框
    TextView tvAgreement; // 协议文本

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 【UI特效】沉浸式状态栏：让顶部状态栏透明，和背景渐变融为一体
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR); // 状态栏黑色文字
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);

        setContentView(R.layout.activity_main);

        kami = findViewById(R.id.kami);
        cbAgree = findViewById(R.id.cb_agree);
        tvAgreement = findViewById(R.id.tv_agreement);

        // 【UI特效】卡片进场动画：启动时卡片从下方平滑升起并淡入
        View cardLogin = findViewById(R.id.card_login);
        cardLogin.setTranslationY(150f);
        cardLogin.setAlpha(0f);
        cardLogin.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();

        // 1. 初始化协议文本链接
        initAgreementText();

        // 2. 恢复勾选状态
        SharedPreferences sp = getSharedPreferences("config", MODE_PRIVATE);
        boolean isAgreed = sp.getBoolean("is_agreed", false);
        cbAgree.setChecked(isAgreed);

        // 3. 监听勾选变化并保存
        cbAgree.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sp.edit().putBoolean("is_agreed", isChecked).apply();
        });

        // 4. 自动填充卡密
        String savedKami = sp.getString("last_kami", "");
        if (!savedKami.isEmpty()) {
            kami.setText(savedKami);
            // 只有当“已勾选”且“有卡密”时，才尝试自动登录
            if (isAgreed) {
                login(null);
            }
        }

        update();
    }

    // 设置富文本：让“<使用条款>”变色且可点击
    // 设置富文本：让“<使用条款>”变色且可点击
    private void initAgreementText() {
        String fullText = "我已知晓并同意<使用条款>";
        SpannableString ss = new SpannableString(fullText);

        // 这里的 8 和 14 是 "<使用条款>" 在字符串中的起始和结束位置
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                showDisclaimerDialog(); // 点击后显示弹窗
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                // 【UI修改】配合新的高级蓝按钮，将协议链接颜色改为 #4A72FF
                ds.setColor(Color.parseColor("#4A72FF"));
                ds.setUnderlineText(false); // 去掉下划线
                ds.setFakeBoldText(true);   // 加粗一点点显得更精致
            }
        };

        ss.setSpan(clickableSpan, 7, 13, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tvAgreement.setText(ss);
        tvAgreement.setMovementMethod(LinkMovementMethod.getInstance()); // 必须设置这个才能点击
        tvAgreement.setHighlightColor(Color.TRANSPARENT);
    }

    // 显示免责声明弹窗
    private void showDisclaimerDialog() {
        new AlertDialog.Builder(this)
                .setTitle("软件使用免责声明")
                .setMessage(DISCLAIMER_TEXT)
                .setPositiveButton("我已阅读", null)
                .show();
    }

    // 检测更新 (核心逻辑未改动)
    public void update() {
        Http.get(WEIURL + "/api/?id=ini&app=" + WEIAID, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {}

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try {
                    if (response.body() == null) return;
                    String body = response.body().string();
                    String WEIINI = Rc4Util.decryRC4(body, RC4KEY, "UTF-8");
                    JSONObject jsonObject = new JSONObject(WEIINI);
                    if (jsonObject.getString("code").equals("200")) {
                        JSONObject json = new JSONObject(jsonObject.getString("msg"));
                        String version = json.getString("version");
                        String app_update_show = json.getString("app_update_show");
                        String app_update_url = json.getString("app_update_url");
                        String app_update_must = json.getString("app_update_must");

                        if (!version.equals(EDITION)) {
                            runOnUiThread(() -> {
                                try {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
                                            .setTitle("有新版本")
                                            .setMessage(app_update_show)
                                            .setPositiveButton("更新", (dialogInterface, i) -> {
                                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(app_update_url));
                                                startActivity(intent);
                                            });

                                    if ("y".equals(app_update_must)) {
                                        builder.setTitle("必须更新");
                                        builder.setCancelable(false);
                                    }
                                    builder.show();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static String stringToMD5(String string) {
        try {
            byte[] hash = MessageDigest.getInstance("MD5").digest(string.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                if ((b & 0xFF) < 0x10) hex.append("0");
                hex.append(Integer.toHexString(b & 0xFF));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }

    // 验证激活 (核心逻辑未改动)
    @SuppressLint("HardwareIds")
    public void login(View view) {
        if (!cbAgree.isChecked()) {
            Toast.makeText(this, "请先阅读并同意《使用条款》", Toast.LENGTH_SHORT).show();
            return;
        }

        String codeText = kami.getText().toString();
        if (codeText.isEmpty()) {
            Toast.makeText(this, "请输入激活码", Toast.LENGTH_SHORT).show();
            return;
        }

        String ANDROID_ID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        long TIME = new Date().getTime();
        double VALUE = 1 + Math.random() * (10 - 1 + 1) + TIME;
        String SIGN = stringToMD5("kami=" + codeText + "&markcode=" + ANDROID_ID + "&t=" + TIME + "&" + WEIKEY);

        String DATA = "";
        try {
            DATA = "data=" + Rc4Util.encryRC4String("kami=" + codeText + "&markcode=" + ANDROID_ID + "&t=" + TIME + "&sign=" + SIGN + "&value=" + VALUE, RC4KEY, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return;
        }

        Http.get(WEIURL + "/api/?id=kmlogon&app=" + WEIAID + "&" + DATA, new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "连接服务器失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                try {
                    if (response.body() == null) return;
                    String body = response.body().string();

                    runOnUiThread(() -> {
                        try {
                            String WEIDATA = Rc4Util.decryRC4(body, RC4KEY, "UTF-8");
                            JSONObject jsonObject = new JSONObject(WEIDATA);
                            String code = jsonObject.getString("code");
                            String msg = jsonObject.getString("msg");
                            String check = jsonObject.getString("check");
                            String time = jsonObject.getString("time");

                            if (code.equals(DLCODE)) {
                                SharedPreferences sp = getSharedPreferences("config", MODE_PRIVATE);
                                sp.edit().putString("last_kami", codeText).apply();

                                JSONObject json = new JSONObject(msg);
                                String vip = json.getString("vip");

                                if (check.equals(stringToMD5(time + WEIKEY + VALUE))) {
                                    try {
                                        Intent intent = new Intent(MainActivity.this, NewActivity.class);
                                        startActivity(intent);
                                        finish();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        return;
                                    }

                                    GregorianCalendar time_vip = new GregorianCalendar();
                                    time_vip.setTimeInMillis(Long.parseLong(vip) * 1000);

                                } else {
                                    Toast.makeText(MainActivity.this, "数据校验失败", Toast.LENGTH_SHORT).show();
                                }
                            } else {
                                SharedPreferences sp = getSharedPreferences("config", MODE_PRIVATE);
                                sp.edit().remove("last_kami").apply();
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            Toast.makeText(MainActivity.this, "解析错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}