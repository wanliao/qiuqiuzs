package com.mei.hua;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MemoryManager {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private static final ConcurrentHashMap<String, ScheduledFuture<?>> freezeTasks = new ConcurrentHashMap<>();

    public static int getProcessID(String pkgName) {
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

    /**
     * 完美模拟 GG 修改器 libunity.so:bss 的寻址逻辑
     * 包含 libunity.so 本身的 rw-p 段，以及紧随其后的 [anon:.bss] 段
     */
    public static List<Long> getModuleBases(int pid, String moduleName) {
        List<Long> bases = new ArrayList<>();
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("cat /proc/" + pid + "/maps\n");
            os.writeBytes("exit\n");
            os.flush();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean lastWasModule = false;

            while ((line = reader.readLine()) != null) {
                boolean containsModule = line.contains(moduleName);

                if (line.contains("rw-p")) {
                    if (containsModule || (lastWasModule && line.contains("[anon:.bss]"))) {
                        String[] parts = line.trim().split("\\s+")[0].split("-");
                        long startAddr = new BigInteger(parts[0], 16).longValue();
                        bases.add(startAddr);
                    }
                }

                // 状态机记录：判断 [anon:.bss] 是否属于上面的模块
                if (containsModule) {
                    lastWasModule = true;
                } else if (!line.contains("[anon:.bss]")) {
                    lastWasModule = false;
                }
            }
        } catch (Exception e) {}
        return bases;
    }

    /**
     * 【核心黑科技】：分页读取法，防 dd 命令 32位 skip 溢出
     */
    public static long readPointer64(int pid, long address) {
        try {
            // 用 4096 字节为一块，大幅度降低 skip 的数值，防止溢出
            long blockSize = 4096;
            long skip = address / blockSize;
            int offset = (int) (address % blockSize);

            // 如果 8 个字节跨越了 4096 边界，则读取两个块
            int count = (offset + 8 > blockSize) ? 2 : 1;

            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd if=/proc/" + pid + "/mem bs=" + blockSize + " skip=" + skip + " count=" + count + " 2>/dev/null"});
            InputStream in = p.getInputStream();
            byte[] bytes = new byte[(int)blockSize * count];
            int readCount = 0;

            while (readCount < bytes.length) {
                int r = in.read(bytes, readCount, bytes.length - readCount);
                if (r == -1) break;
                readCount += r;
            }
            p.waitFor();

            // 将读取到的区块转移到 Java 层切割提取精准的 8 字节
            if (readCount >= offset + 8) {
                byte[] ptrBytes = new byte[8];
                System.arraycopy(bytes, offset, ptrBytes, 0, 8);
                return ByteBuffer.wrap(ptrBytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
            }
        } catch (Exception e) {}
        return 0;
    }

    public static boolean writeMemoryFloat(int pid, long address, float value) {
        try {
            byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(value).array();
            return executeWrite(pid, address, bytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean writeMemoryDword(int pid, long address, int value) {
        try {
            byte[] bytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
            return executeWrite(pid, address, bytes);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean executeWrite(int pid, long address, byte[] bytes) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "dd of=/proc/" + pid + "/mem bs=1 seek=" + address + " count=4 conv=notrunc 2>/dev/null"});
        OutputStream out = p.getOutputStream();
        out.write(bytes);
        out.flush();
        out.close();
        p.waitFor();
        return true;
    }

    public static void stopFreeze(String key) {
        ScheduledFuture<?> task = freezeTasks.get(key);
        if (task != null) {
            task.cancel(true);
            freezeTasks.remove(key);
        }
    }

    public static void startFreezeFloat(String key, String pkgName, String hexAddress, Float[] valueRef) {
        stopFreeze(key);
        long address = new BigInteger(hexAddress, 16).longValue();

        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> {
            int pid = getProcessID(pkgName);
            if (pid != -1 && valueRef[0] != null) {
                writeMemoryFloat(pid, address, valueRef[0]);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        freezeTasks.put(key, task);
    }
    /**
     * 【教学用】：单次解链并写入 Float 数值（不冻结）
     */
    public static void writePointerChainFloatOnce(Context context, String pkgName, String moduleName, long[] offsets, float finalValue) {
        new Thread(() -> {
            int pid = getProcessID(pkgName);
            if (pid == -1) return;

            // 获取该模块所有的 rw-p 段
            List<Long> bases = getModuleBases(pid, moduleName);
            if (bases.isEmpty()) return;

            boolean success = false;
            for (int i = 0; i < bases.size(); i++) {
                long currentAddr = bases.get(i) + offsets[0];
                boolean isValidChain = true;

                // 逐级读取指针
                for (int j = 1; j < offsets.length; j++) {
                    long nextPtr = readPointer64(pid, currentAddr);
                    if (nextPtr == 0) {
                        isValidChain = false;
                        break;
                    }
                    currentAddr = nextPtr + offsets[j];
                }

                // 解链成功，单次写入并跳出
                if (isValidChain && currentAddr != 0) {
                    writeMemoryFloat(pid, currentAddr, finalValue);
                    success = true;
                    break;
                }
            }

            // 提示修改结果
            if (success) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "视野已修改为: " + finalValue, Toast.LENGTH_SHORT).show()
                );
            }
        }).start();
    }
    public static void startFreezePointerChain(Context context, String key, String pkgName, String moduleName, long[] offsets, int finalValue) {
        stopFreeze(key);

        final boolean[] hasToasted = {false};

        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(() -> {
            int pid = getProcessID(pkgName);
            if (pid == -1) return;

            List<Long> bases = getModuleBases(pid, moduleName);

            if (bases.isEmpty() && !hasToasted[0]) {
                new Handler(Looper.getMainLooper()).post(() ->
                        Toast.makeText(context, "找不到 " + moduleName + " 的 bss 段", Toast.LENGTH_LONG).show()
                );
                hasToasted[0] = true;
                return;
            }

            StringBuilder debugLog = new StringBuilder("【链条调试信息】\n");
            boolean foundAny = false;

            // 遍历所有可能的 BSS 段进行解链
            for (int i = 0; i < bases.size(); i++) {
                long base = bases.get(i);
                long currentAddr = base + offsets[0];

                debugLog.append("段").append(i).append(" [").append(Long.toHexString(base)).append("]->");

                boolean isValidChain = true;
                for (int j = 1; j < offsets.length; j++) {
                    long nextPtr = readPointer64(pid, currentAddr);
                    if (nextPtr == 0) {
                        debugLog.append("断在").append(j).append("级\n");
                        isValidChain = false;
                        break;
                    }
                    currentAddr = nextPtr + offsets[j];
                }

                // 成功解出最后一条链并写入
                if (isValidChain && currentAddr != 0) {
                    debugLog.append("成功解链! 写入地址: ").append(Long.toHexString(currentAddr));
                    writeMemoryDword(pid, currentAddr, finalValue);
                    foundAny = true;
                    break;
                }
            }

            if (!hasToasted[0]) {
                final String finalLog = debugLog.toString();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, finalLog, Toast.LENGTH_LONG).show();
                    Log.e("MemoryHackDebug", finalLog);
                });
                hasToasted[0] = true;
            }

        }, 0, 1000, TimeUnit.MILLISECONDS);

        freezeTasks.put(key, task);
    }
}