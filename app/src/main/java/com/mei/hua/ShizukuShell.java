package com.mei.hua;

import android.util.Log;
import java.lang.reflect.Method;
import rikka.shizuku.Shizuku;

public class ShizukuShell {

    private static final String TAG = "ShizukuShell";

    public boolean execute(String command) {
        if (!isPermissionGranted()) {
            Log.e(TAG, "Shizuku permission is not granted. Cannot execute command.");
            return false;
        }

        Process process = null;
        try {
            Log.d(TAG, "Executing command via Shizuku (using Reflection to bypass compile error).");
            String[] cmd = {"sh", "-c", command};

            // ---- Java Reflection ----
            // This is an unconventional workaround for a persistent, environment-specific
            // compile error where the compiler incorrectly sees the public 'newProcess' method as private.
            // We dynamically find the method at runtime to bypass the faulty compile-time check.
            Method newProcessMethod = Shizuku.class.getMethod("newProcess", String[].class, String[].class, String.class);

            // 'newProcess' is a static method, so the first argument to 'invoke' is null.
            // The second argument is an array of the arguments for the method.
            Object processObject = newProcessMethod.invoke(null, new Object[]{cmd, null, null});
            process = (Process) processObject;
            // ---- End of Reflection ----

            int exitCode = process.waitFor();
            Log.d(TAG, "Command exited with code: " + exitCode);
            return exitCode == 0;

        } catch (Throwable e) {
            // This will catch everything, including reflection-specific errors like NoSuchMethodException
            Log.e(TAG, "An error occurred while executing Shizuku command with reflection.", e);
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private boolean isPermissionGranted() {
        if (Shizuku.isPreV11()) {
            return Shizuku.pingBinder();
        }
        return Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
}
