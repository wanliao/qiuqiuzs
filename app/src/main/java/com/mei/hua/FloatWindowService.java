package com.mei.hua;

import android.app.Service;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.math.BigInteger;

public class FloatWindowService extends Service {

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;

    private FrameLayout rootLayout;
    private ImageView floatingBall;
    private LinearLayout floatingMenu;
    private LinearLayout contentArea;
    private android.content.SharedPreferences sp;

    private String targetAddressHex;
    private static final String TARGET_PKG = "com.ztgame.bob";

    private static final int COLOR_BG_DARK = Color.parseColor("#E61E1E1E");
    private static final int COLOR_TITLE_BG = Color.parseColor("#FF2D2D30");
    private static final int COLOR_ACCENT = Color.parseColor("#4A72FF");
    private static final int COLOR_TEXT = Color.WHITE;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetAddressHex = intent.getStringExtra("BASE_ADDRESS");
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        sp = getSharedPreferences("BobHelperConfig", MODE_PRIVATE);
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingWindow();
    }

    private void createFloatingWindow() {
        params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
        params.gravity = Gravity.TOP | Gravity.START;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.x = 100;
        params.y = 200;

        rootLayout = new FrameLayout(this);

        initFloatingMenu();
        initFloatingBall();

        rootLayout.addView(floatingMenu);
        rootLayout.addView(floatingBall);
        floatingMenu.setVisibility(View.GONE);

        rootLayout.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                if (floatingMenu.getVisibility() == View.VISIBLE) {
                    collapseMenu();
                    return true;
                }
            }
            return false;
        });

        windowManager.addView(rootLayout, params);
    }

    private void initFloatingBall() {
        floatingBall = new ImageView(this);
        floatingBall.setImageResource(R.mipmap.ic_launcher_round);

        int size = dp2px(45);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        floatingBall.setLayoutParams(lp);

        setupDragAndClick(floatingBall, true);
    }

    private void initFloatingMenu() {
        floatingMenu = new LinearLayout(this);
        floatingMenu.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_BG_DARK);
        bg.setCornerRadius(dp2px(8));
        bg.setStroke(dp2px(1), Color.parseColor("#333333"));
        floatingMenu.setBackground(bg);

        FrameLayout.LayoutParams menuLp = new FrameLayout.LayoutParams(dp2px(260), WindowManager.LayoutParams.WRAP_CONTENT);
        floatingMenu.setLayoutParams(menuLp);

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable titleBg = new GradientDrawable();
        titleBg.setColor(COLOR_TITLE_BG);
        titleBg.setCornerRadii(new float[]{dp2px(8), dp2px(8), dp2px(8), dp2px(8), 0, 0, 0, 0});
        titleBar.setBackground(titleBg);
        titleBar.setPadding(dp2px(10), dp2px(5), dp2px(10), dp2px(5));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("小助手菜单");
        tvTitle.setTextColor(COLOR_TEXT);
        tvTitle.setTextSize(12);
        tvTitle.getPaint().setFakeBoldText(true);
        LinearLayout.LayoutParams titleTextLp = new LinearLayout.LayoutParams(0, WindowManager.LayoutParams.WRAP_CONTENT, 1);
        tvTitle.setLayoutParams(titleTextLp);

        TextView btnExit = new TextView(this);
        btnExit.setText("[退出]");
        btnExit.setTextColor(Color.parseColor("#FF5555"));
        btnExit.setTextSize(10);
        btnExit.setPadding(0, 0, dp2px(10), 0);
        btnExit.setOnClickListener(v -> {
            stopSelf();
            System.exit(0);
        });

        TextView btnCollapse = new TextView(this);
        btnCollapse.setText("—");
        btnCollapse.setTextColor(Color.LTGRAY);
        btnCollapse.setTextSize(12);
        btnCollapse.setOnClickListener(v -> collapseMenu());

        titleBar.addView(tvTitle);
        titleBar.addView(btnExit);
        titleBar.addView(btnCollapse);
        floatingMenu.addView(titleBar);
        setupDragAndClick(titleBar, false);

        contentArea = new LinearLayout(this);
        contentArea.setOrientation(LinearLayout.VERTICAL);
        contentArea.setPadding(dp2px(12), dp2px(10), dp2px(12), dp2px(10));

        // 加载功能模块
        contentArea.addView(createSpeedControlBlock("全局变速"));
        contentArea.addView(createExitButtonBlock());
        contentArea.addView(createFov2ControlBlock("视野1"));
        contentArea.addView(createFovControlBlock("视野2"));

        floatingMenu.addView(contentArea);
    }

    private View createSpeedControlBlock(String title) {
        LinearLayout blockLayout = new LinearLayout(this);
        blockLayout.setOrientation(LinearLayout.VERTICAL);
        blockLayout.setPadding(0, dp2px(5), 0, dp2px(5));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(title);
        checkBox.setTextColor(COLOR_TEXT);
        checkBox.setTextSize(13);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkBox.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setVisibility(View.GONE);
        controlPanel.setPadding(dp2px(25), dp2px(5), 0, dp2px(5));

        TextView tvValue = new TextView(this);
        tvValue.setText("当前倍数: 1.0 (已冻结)");
        tvValue.setTextColor(Color.parseColor("#AAAAAA"));
        tvValue.setTextSize(11);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(49);
        seekBar.setProgress(0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        controlPanel.addView(tvValue);
        controlPanel.addView(seekBar);
        blockLayout.addView(checkBox);
        blockLayout.addView(controlPanel);

        final Float[] currentSpeedRef = { 1.0f };

        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            controlPanel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                if (targetAddressHex == null || targetAddressHex.isEmpty()) {
                    showToast("基址未设置");
                    checkBox.setChecked(false);
                    return;
                }
                currentSpeedRef[0] = seekBar.getProgress() + 1.0f;
                MemoryManager.startFreezeFloat("GLOBAL_SPEED", TARGET_PKG, targetAddressHex, currentSpeedRef);
                showToast("全局变速 已开启并冻结");
            } else {
                MemoryManager.stopFreeze("GLOBAL_SPEED");
                new Thread(() -> {
                    int pid = MemoryManager.getProcessID(TARGET_PKG);
                    if (pid != -1) {
                        MemoryManager.writeMemoryFloat(pid, new BigInteger(targetAddressHex, 16).longValue(), 1.0f);
                    }
                }).start();
                showToast("全局变速 已关闭");
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float val = progress + 1.0f;
                tvValue.setText("当前倍数: " + val + " (已冻结)");
                currentSpeedRef[0] = val;
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        return blockLayout;
    }

    /**
     * 【已修复】采用新的偏移量链
     */
    private View createExitButtonBlock() {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText("退出按钮 (带基址追踪调试)");
        checkBox.setTextColor(COLOR_TEXT);
        checkBox.setTextSize(13);
        checkBox.setPadding(0, dp2px(5), 0, dp2px(5));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkBox.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        // 你的多级偏移量
        final long[] offsets = {0x1CC60L, 0x130L, 0x18L, 0x28L, 0x204L};

        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                // 注意这里第一个参数传入了 FloatWindowService.this 作为上下文
                MemoryManager.startFreezePointerChain(
                        FloatWindowService.this,
                        "EXIT_BUTTON",
                        TARGET_PKG,
                        "libunity.so",
                        offsets,
                        8
                );
                showToast("正在解析锁链，请看屏幕提示...");
            } else {
                MemoryManager.stopFreeze("EXIT_BUTTON");
                showToast("退出按钮取消锁定");
            }
        });
        return checkBox;
    }

    private void collapseMenu() {
        floatingMenu.setVisibility(View.GONE);
        floatingBall.setVisibility(View.VISIBLE);
    }
    /**
     * 【教学用】：创建带记忆功能的“视野扩大”模块
     */
    private View createFovControlBlock(String title) {
        LinearLayout blockLayout = new LinearLayout(this);
        blockLayout.setOrientation(LinearLayout.VERTICAL);
        blockLayout.setPadding(0, dp2px(5), 0, dp2px(5));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(title);
        checkBox.setTextColor(COLOR_TEXT);
        checkBox.setTextSize(13);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkBox.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setVisibility(View.GONE);
        controlPanel.setPadding(dp2px(25), dp2px(5), 0, dp2px(5));

        TextView tvValue = new TextView(this);
        tvValue.setTextColor(Color.parseColor("#AAAAAA"));
        tvValue.setTextSize(11);

        // 初始化滑动条 (0~35 对应 1.5~5.0)
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(35);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        // ================= 记忆功能核心 =================
        // 读取保存的进度，如果没有存过，默认是 0（也就是 1.5）
        int savedProgress = sp.getInt("FOV_PROGRESS", 0);
        seekBar.setProgress(savedProgress);

        // 初始化显示的文本
        float initialFov = 1.5f + (savedProgress / 10.0f);
        tvValue.setText("当前视野: " + initialFov);

        controlPanel.addView(tvValue);
        controlPanel.addView(seekBar);
        blockLayout.addView(checkBox);
        blockLayout.addView(controlPanel);

        // ================= 这里是填写 Lua 偏移量的地方 =================
        // 教学：Lua 里的数组是 {123, 456, 789}，Java 里就在后面加个 L 代表长整数就行
        // 请你打开你的 视野.lua，把 readPointer 后面的那个大括号里的数字抄进来！

        final long[] offsets = {0x1CC60L, 0x168L, 0x3C8L, 0xA8L, 0x28L, 0x74L};
        // 勾选框事件
        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            controlPanel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                // 读取滑动条进度，计算实际视野并写入
                float currentFov = 1.5f + (seekBar.getProgress() / 10.0f);
                MemoryManager.writePointerChainFloatOnce(
                        FloatWindowService.this, TARGET_PKG, "libunity.so", offsets, currentFov
                );
            } else {
                // 取消勾选时，恢复为默认视野 1.0f
                MemoryManager.writePointerChainFloatOnce(
                        FloatWindowService.this, TARGET_PKG, "libunity.so", offsets, 1.0f
                );
                showToast("视野扩大已关闭，恢复默认");
            }
        });

        // 滑动条事件
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                float val = 1.5f + (progress / 10.0f);
                tvValue.setText("当前视野: " + val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                // 【松开手时】保存当前滑动条的位置到 SharedPreferences
                sp.edit().putInt("FOV_PROGRESS", sb.getProgress()).apply();

                // 如果当前功能处于勾选状态，松开手时顺便应用一下最新视野
                if (checkBox.isChecked()) {
                    float val = 1.5f + (sb.getProgress() / 10.0f);
                    MemoryManager.writePointerChainFloatOnce(
                            FloatWindowService.this, TARGET_PKG, "libunity.so", offsets, val
                    );
                }
            }
        });

        return blockLayout;
    }
    /**
     * 视野扩大2 模块 (带独立记忆与单次修改)
     */
    /**
     * 视野扩大2 模块 (范围: 0.5 - 5.0)
     */
    private View createFov2ControlBlock(String title) {
        LinearLayout blockLayout = new LinearLayout(this);
        blockLayout.setOrientation(LinearLayout.VERTICAL);
        blockLayout.setPadding(0, dp2px(5), 0, dp2px(5));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(title);
        checkBox.setTextColor(COLOR_TEXT);
        checkBox.setTextSize(13);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkBox.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        LinearLayout controlPanel = new LinearLayout(this);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setVisibility(View.GONE);
        controlPanel.setPadding(dp2px(25), dp2px(5), 0, dp2px(5));

        TextView tvValue = new TextView(this);
        tvValue.setTextColor(Color.parseColor("#AAAAAA"));
        tvValue.setTextSize(11);

        SeekBar seekBar = new SeekBar(this);
        // 【修改点 1】: 最大刻度改为 45 (代表最大增加 4.5 的数值)
        seekBar.setMax(45);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(COLOR_ACCENT));
            seekBar.setThumbTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        int savedProgress = sp.getInt("FOV2_PROGRESS", 0);
        seekBar.setProgress(savedProgress);

        // 【修改点 2】: 基础值改为 0.5f
        float initialFov = 0.5f + (savedProgress / 10.0f);
        tvValue.setText("当前视野: " + initialFov);

        controlPanel.addView(tvValue);
        controlPanel.addView(seekBar);
        blockLayout.addView(checkBox);
        blockLayout.addView(controlPanel);

        final long[] offsets = {0x1CC60L, 0x130L, 0x18L, 0x28L, 0x7DCL};

        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            controlPanel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                // 【修改点 3】: 基础值改为 0.5f
                float currentFov = 0.5f + (seekBar.getProgress() / 10.0f);
                MemoryManager.writePointerChainFloatOnce(
                        FloatWindowService.this, TARGET_PKG, "libunity.so", offsets, currentFov
                );
            } else {
                // 取消勾选时，我这里默认让它恢复到 1.0f (你可以根据游戏实际正常视野修改这个 1.0f)
                MemoryManager.writePointerChainFloatOnce(
                        FloatWindowService.this, TARGET_PKG, "libunity.so", offsets, 0.5f
                );
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                // 【修改点 4】: 基础值改为 0.5f
                float val = 0.5f + (progress / 10.0f);
                tvValue.setText("当前视野: " + val);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                sp.edit().putInt("FOV2_PROGRESS", sb.getProgress()).apply();

                if (checkBox.isChecked()) {
                    // 【修改点 5】: 基础值改为 0.5f
                    float val = 0.5f + (sb.getProgress() / 10.0f);
                    MemoryManager.writePointerChainFloatOnce(
                            FloatWindowService.this, TARGET_PKG, "libunity.so", offsets, val
                    );
                }
            }
        });

        return blockLayout;
    }
    private void setupDragAndClick(View view, boolean isBall) {
        view.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isMoved = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        isMoved = false;
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isMoved = true;
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;
                            windowManager.updateViewLayout(rootLayout, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!isMoved && isBall) {
                            floatingBall.setVisibility(View.GONE);
                            floatingMenu.setVisibility(View.VISIBLE);
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void showToast(String msg) {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MemoryManager.stopFreeze("GLOBAL_SPEED");
        MemoryManager.stopFreeze("EXIT_BUTTON");
        if (rootLayout != null) windowManager.removeView(rootLayout);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}