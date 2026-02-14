package com.mei.hua;

import android.app.Service;
import android.content.Intent;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FloatWindowService extends Service {

    private WindowManager windowManager;
    private LinearLayout layoutContainer;

    private String targetAddressHex; // 目标基址
    private boolean isToggleStateOne = true; // 功能2的切换状态
    private static final String TARGET_PKG = "com.ztgame.bob";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            targetAddressHex = intent.getStringExtra("BASE_ADDRESS");
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createFloatingWindow();
    }

    private void createFloatingWindow() {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.y = 0;

        layoutContainer = new LinearLayout(this);
        layoutContainer.setOrientation(LinearLayout.HORIZONTAL);
        layoutContainer.setPadding(10, 10, 10, 10);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#80000000"));
        bg.setCornerRadius(dp2px(20));
        layoutContainer.setBackground(bg);

        // 按钮 1: 断网 + 改999
        addButton("断+999", Color.RED, v -> runFunction1());

        // 按钮 2: 切换 8 / 1
        addButton("改8/1", Color.BLUE, v -> runFunction2());

        addButton("X", Color.GRAY, v -> {
            stopSelf();
            System.exit(0);
        });

        windowManager.addView(layoutContainer, params);

        layoutContainer.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(layoutContainer, params);
                        return true;
                }
                return false;
            }
        });
    }

    private void addButton(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(12);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        btn.setBackground(shape);
        int size = dp2px(50);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(10, 0, 10, 0);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        layoutContainer.addView(btn);
    }

    // ==========================================
    // 核心功能逻辑区
    // ==========================================

    /**
     * 功能1: 关闭WiFi/数据 -> 改999 -> 等待3秒 -> 恢复
     */
    /**
     * 功能1: 关闭WiFi/数据 -> 改999 -> 等待3秒 -> 恢复网络 -> 马上改1
     */
    private void runFunction1() {
        if (targetAddressHex == null || targetAddressHex.isEmpty()) {
            showToast("基址未设置");
            return;
        }

        new Thread(() -> {
            try {
                int pid = getProcessID(TARGET_PKG);
                if (pid == -1) {
                    showToast("游戏未运行");
                    return;
                }

                // 1. 物理断网
                executeRootCmd("svc wifi disable");
                executeRootCmd("svc data disable");

                // 2. 立即改内存 -> 999.0
                writeMemoryFloat(pid, targetAddressHex, 999.0f);

                showToast("断网 & 改999 完成，等待3秒...");

                // 3. 延时 3秒
                Thread.sleep(1500);

                // 4. 恢复网络
                executeRootCmd("svc wifi enable");
                executeRootCmd("svc data enable");

                // 5. 【新增】网络恢复后，立即改回 1.0
                writeMemoryFloat(pid, targetAddressHex, 1.0f);

                showToast("网络已恢复 & 数值已重置为 1");
            } catch (Exception e) {
                // 异常保护：恢复网络并尝试重置数值
                executeRootCmd("svc wifi enable");
                executeRootCmd("svc data enable");
                if (getProcessID(TARGET_PKG) != -1) {
                    writeMemoryFloat(getProcessID(TARGET_PKG), targetAddressHex, 1.0f);
                }
                e.printStackTrace();
            }
        }).start();
    }


    /**
     * 功能2: 切换改 8.0 / 1.0
     */
    private void runFunction2() {
        if (targetAddressHex == null || targetAddressHex.isEmpty()) {
            showToast("基址未设置");
            return;
        }

        new Thread(() -> {
            int pid = getProcessID(TARGET_PKG);


            float val = isToggleStateOne ? 8.0f : 1.0f;
            boolean success = writeMemoryFloat(pid, targetAddressHex, val);

            if (success) {
                showToast("已修改为 " + val);
                isToggleStateOne = !isToggleStateOne; // 切换状态
            } else {
                showToast("修改失败");
            }
        }).start();
    }

    // --- 内存写入工具 ---

    private boolean writeMemoryFloat(int pid, String hexAddress, float value) {
        try {
            long address = new BigInteger(hexAddress, 16).longValue();

            // Float 转 hex string (Little Endian)
            byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
            StringBuilder hexString = new StringBuilder();
            for (byte b : bytes) {
                hexString.append(String.format("\\x%02x", b));
            }

            // dd 命令写入
            String cmd = "echo -n -e '" + hexString.toString() + "' | dd of=/proc/" + pid + "/mem bs=1 seek=" + address + " count=4 conv=notrunc";

            executeRootCmd(cmd);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
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

    private void executeRootCmd(String command) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showToast(String msg) {
        try {
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
        } catch (Exception e) {}
    }

    private int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (layoutContainer != null) {
            windowManager.removeView(layoutContainer);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
