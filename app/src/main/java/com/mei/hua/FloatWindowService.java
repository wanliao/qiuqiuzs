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
import android.view.ViewGroup;
import android.widget.Button;

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
        tvTitle.setText("6-3-1");
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

        contentArea.addView(createTimeJumpBlock("时间跳跃"));
        contentArea.addView(createSpeedControlBlock("全局变速"));
        contentArea.addView(createExitButtonBlock());
        contentArea.addView(createFov2ControlBlock("视野1"));
        contentArea.addView(createFovControlBlock("视野2"));
        contentArea.addView(createSpitAccelBlock("测试功能(勿开)"));


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
        checkBox.setText("退出按钮(内存版)");
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
    /**
     * 解连吐模块 (单次写入 Float)
     */
    private View createFastSpitBlock(String title) {
        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(title);
        checkBox.setTextColor(COLOR_TEXT);
        checkBox.setTextSize(13);
        checkBox.setPadding(0, dp2px(5), 0, dp2px(5));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkBox.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        // 填入你给的 libil2cpp.so 锁链
        final long[] offsets = {0x157518L, 0x850L, 0xC8L, 0x85CL};

        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                // 开启时写入 -1.0f
                MemoryManager.writePointerChainFloatOnceFromBase(
                        FloatWindowService.this, TARGET_PKG, "libil2cpp.so", offsets, -1.0f
                );
            } else {
                // 取消勾选时，恢复游戏的默认吐球 CD。
                // 我这里先写了 0.1f，你可以根据游戏实际的正常数值自行修改
                MemoryManager.writePointerChainFloatOnceFromBase(
                        FloatWindowService.this, TARGET_PKG, "libil2cpp.so", offsets, 0.1f
                );
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
    /**
     * 真正的视野2模块：
     * 勾选 -> 执行 libsiuy.so
     * 取消勾选 -> 执行 liboins.so (恢复/修改地址)
     */
    private View createFovControlBlock(String title) {
        LinearLayout blockLayout = new LinearLayout(this);
        blockLayout.setOrientation(LinearLayout.VERTICAL);
        blockLayout.setPadding(0, dp2px(5), 0, dp2px(5));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(title + " (备用)");
        checkBox.setTextColor(COLOR_TEXT);
        checkBox.setTextSize(13);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            checkBox.setButtonTintList(ColorStateList.valueOf(COLOR_ACCENT));
        }

        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                // ================= 开启时：执行 libsiuy.so =================
                new Thread(() -> {
                    String exePath = copyAssetToCache("libsiuy.so");
                    if (exePath != null) {
                        execRootCmd("chmod 777 " + exePath);
                        execRootCmd("su -c " + exePath);

                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            Toast.makeText(FloatWindowService.this, "视野2已开启：成功运行 libsiuy", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            Toast.makeText(FloatWindowService.this, "开启失败，assets缺少 libsiuy.so", Toast.LENGTH_LONG).show();
                            checkBox.setChecked(false);
                        });
                    }
                }).start();
            } else {
                // ================= 关闭时：执行 liboins.so =================
                new Thread(() -> {
                    // 尝试杀掉之前开启的程序，防止冲突
                    execRootCmd("pkill -f libsiuy.so");

                    // 提取恢复用的文件 liboins.so
                    String restorePath = copyAssetToCache("liboins.so");
                    if (restorePath != null) {
                        // 赋予权限并执行
                        execRootCmd("chmod 777 " + restorePath);
                        execRootCmd("su -c " + restorePath);

                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            Toast.makeText(FloatWindowService.this, "视野2已关闭：成功运行 liboins", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            Toast.makeText(FloatWindowService.this, "关闭失败，assets缺少 liboins.so", Toast.LENGTH_LONG).show();
                        });
                    }
                }).start();
            }
        });

        blockLayout.addView(checkBox);
        return blockLayout;
    }
    /**
     * 辅助工具：把 assets 里的文件复制到手机本地，方便后续执行
     */
    private String copyAssetToCache(String fileName) {
        try {
            // 获取缓存目录，将文件放到这里
            java.io.File cacheFile = new java.io.File(getCacheDir(), fileName);

            // 如果文件不存在，就从 assets 复制出来
            if (!cacheFile.exists()) {
                java.io.InputStream is = getAssets().open(fileName);
                java.io.FileOutputStream fos = new java.io.FileOutputStream(cacheFile);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
                fos.flush();
                fos.close();
                is.close();
            }
            // 返回本地文件的绝对路径（类似 /data/user/0/com.mei.hua/cache/libsiuy.so）
            return cacheFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null; // 发生错误返回空
        }
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
    /**
     * 新增：吐球加速模块 (单向开启)
     * 开启 -> 执行 libtmxqu.so
     * 关闭 -> 提示无法关闭，并保持勾选状态
     */
    private View createSpitAccelBlock(String title) {
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

        // 这是一个小技巧：用来防止程序自己把勾选框改回“打勾”状态时，又重复执行一遍开启代码
        final boolean[] isForcingCheck = {false};

        checkBox.setOnCheckedChangeListener((btn, isChecked) -> {
            // 如果是我们代码强制让它保持打勾触发的，就直接跳过，啥也不干
            if (isForcingCheck[0]) {
                isForcingCheck[0] = false;
                return;
            }

            if (isChecked) {
                // ================= 开启时：执行 libtmxqu.so =================
                new Thread(() -> {
                    String exePath = copyAssetToCache("libtmxqu.so");
                    if (exePath != null) {
                        execRootCmd("chmod 777 " + exePath);
                        execRootCmd("su -c " + exePath);

                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            Toast.makeText(FloatWindowService.this, "吐球加速已成功开启！", Toast.LENGTH_SHORT).show();
                        });
                    } else {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                            Toast.makeText(FloatWindowService.this, "开启失败，assets缺少 libtmxqu.so", Toast.LENGTH_LONG).show();
                            // 如果因为缺文件导致开启失败，那还是允许取消打勾的
                            isForcingCheck[0] = true;
                            checkBox.setChecked(false);
                        });
                    }
                }).start();
            } else {
                // ================= 取消勾选时：拦截并提示 =================
                // 弹窗提示用户
                Toast.makeText(FloatWindowService.this, "该功能开启后无法关闭！", Toast.LENGTH_SHORT).show();

                // 重点：强制把勾选框重新打上勾
                isForcingCheck[0] = true;
                checkBox.setChecked(true);
            }
        });

        blockLayout.addView(checkBox);
        return blockLayout;
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

// ==========================================
    // 新增：时间跳跃模块 (时间欺骗)
    // ==========================================

    /**
     * 辅助执行独立 Root 命令
     */
    private void execRootCmd(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            java.io.DataOutputStream os = new java.io.DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建时间跳跃按钮模块
     */
    // ==========================================
    // 新增：拟态魔法按钮版 时间跳跃 (带状态反馈 + 始终圆角)
    // ==========================================

    /**
     * 生成一个可以始终保持圆角的 GradientDrawable 背景
     */
    private android.graphics.drawable.GradientDrawable createMagicBg(int solidColor, int strokeColor, int cornerRadius) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(solidColor);
        bg.setCornerRadius(cornerRadius);
        // 加上一层淡淡的高光描边，增加拟态质感
        bg.setStroke(dp2px(2), strokeColor);
        return bg;
    }

    /**
     * 创建高级拟态魔法时间跳跃按钮
     */
    /**
     * 创建高级拟态魔法时间跳跃按钮 (1分钟版)
     */
    private View createTimeJumpBlock(String title) {
        LinearLayout blockLayout = new LinearLayout(this);
        blockLayout.setOrientation(LinearLayout.VERTICAL);
        blockLayout.setPadding(0, dp2px(10), 0, dp2px(15));

        Button btnJump = new Button(this);
        // 修改为 +1 分钟
        btnJump.setText("✨ " + title);
        btnJump.setTextColor(Color.WHITE);
        btnJump.setTextSize(13);
        btnJump.getPaint().setFakeBoldText(true);
        btnJump.setStateListAnimator(null);

        // --- 状态背景生成逻辑 ---
        int radius = dp2px(15);
        int normalGreen = Color.parseColor("#48BB78");
        int pressedGreen = Color.parseColor("#68D391");
        int disabledGray = Color.parseColor("#A0AEC0");
        int strokeColor = Color.parseColor("#FFFFFF");

        android.graphics.drawable.GradientDrawable defaultBg = new android.graphics.drawable.GradientDrawable();
        defaultBg.setColor(normalGreen); defaultBg.setCornerRadius(radius); defaultBg.setStroke(dp2px(2), strokeColor);

        android.graphics.drawable.GradientDrawable pressedBg = new android.graphics.drawable.GradientDrawable();
        pressedBg.setColor(pressedGreen); pressedBg.setCornerRadius(radius); pressedBg.setStroke(dp2px(2), strokeColor);

        android.graphics.drawable.GradientDrawable disabledBg = new android.graphics.drawable.GradientDrawable();
        disabledBg.setColor(disabledGray); disabledBg.setCornerRadius(radius); disabledBg.setStroke(dp2px(2), strokeColor);

        android.graphics.drawable.StateListDrawable magicStates = new android.graphics.drawable.StateListDrawable();
        magicStates.addState(new int[]{-android.R.attr.state_enabled}, disabledBg);
        magicStates.addState(new int[]{android.R.attr.state_pressed}, pressedBg);
        magicStates.addState(new int[]{}, defaultBg);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            btnJump.setBackground(magicStates);
        } else {
            btnJump.setBackgroundDrawable(magicStates);
        }

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp2px(45));
        btnParams.setMargins(dp2px(15), dp2px(5), dp2px(15), dp2px(5));
        btnJump.setLayoutParams(btnParams);

        // --- 点击事件与核心 Root 逻辑 ---
        btnJump.setOnClickListener(v -> {
            btnJump.setText("✨ 执行中... ");
            btnJump.setEnabled(false); // 触发变灰的圆角拟态状态

            new Thread(() -> {
                try {
                    // 1. 关闭系统【自动确定时间】开关
                    execRootCmd("settings put global auto_time 0");

                    // 2. 【核心修改】计算当前时间 + 60秒 (60000毫秒)
                    long targetTime = System.currentTimeMillis() + 60000;

                    // 3. 转换成安卓底层 date 命令支持的标准格式 (月日时分年.秒)
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMddHHmmyyyy.ss");
                    String timeStr = sdf.format(new java.util.Date(targetTime));

                    // 4. 用 Root 强行修改底层时间，并发送广播通知游戏引擎
                    execRootCmd("date " + timeStr);
                    execRootCmd("am broadcast -a android.intent.action.TIME_SET");

                    // 5. 让跳跃后的时间维持 1.5 秒，确保游戏卡出 Bug、跳过倒计时
                    Thread.sleep(1500);

                    // 6. 重新打开【自动确定时间】开关，系统会瞬间联网恢复真实时间
                    execRootCmd("settings put global auto_time 1");
                    execRootCmd("am broadcast -a android.intent.action.TIME_SET");

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // 恢复按钮状态
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        btnJump.setEnabled(true);
                        btnJump.setText("✨ " + title);
                        Toast.makeText(FloatWindowService.this, "已校准", Toast.LENGTH_SHORT).show();
                    });
                }
            }).start();
        });

        blockLayout.addView(btnJump);
        return blockLayout;
    }

}
