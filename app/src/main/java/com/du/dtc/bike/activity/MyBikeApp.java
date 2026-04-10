package com.du.dtc.bike.activity;

import android.app.Application;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import com.du.dtc.bike.activity.CrashReportActivity;
import com.du.dtc.bike.log.BleDebugLogger;

public class MyBikeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Giăng bẫy bắt mọi Exception chưa được xử lý trong ứng dụng
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // 1. Lấy chi tiết đoạn lỗi (Stack Trace)
            String stackTrace = Log.getStackTraceString(throwable);
            BleDebugLogger.e("FATAL_CRASH", stackTrace);

            // 2. Chuyển hướng sang màn hình thông báo lỗi (CrashReportActivity)
            Intent intent = new Intent(getApplicationContext(), CrashReportActivity.class);
            intent.putExtra("CRASH_INFO", stackTrace);

            // Xóa toàn bộ màn hình cũ, ép mở màn hình lỗi trên Task mới
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

            // 3. Khai tử tiến trình bị lỗi để tránh Android hiện bảng "App has stopped" khó
            // chịu
            Process.killProcess(Process.myPid());
            System.exit(1);
        });
    }
}