package com.du.dtc.bike.activity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.du.dtc.bike.R;
import com.du.dtc.bike.log.BleDebugLogger;

public class CrashReportActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash_report);

        TextView tvCrashDetails = findViewById(R.id.tv_crash_details);
        Button btnShare = findViewById(R.id.btn_share_crash);
        Button btnRestart = findViewById(R.id.btn_restart_app);

        // Lấy thông tin lỗi từ bẫy truyền sang
        String crashInfo = getIntent().getStringExtra("CRASH_INFO");
        if (crashInfo != null) {
            tvCrashDetails.setText(crashInfo);

            // 👉 BƯỚC QUAN TRỌNG: Ghi ngược lỗi này vào RAM mới để hàm Export có thể tóm
            // được
            BleDebugLogger.e("CRASH_DUMP", "APP CRASHED WITH ERROR:\n" + crashInfo);
        }

        // Nút Share gọi hàm của bạn
        btnShare.setOnClickListener(v -> {
            BleDebugLogger.exportAndShareLogs(this);
        });

        // Nút Khởi động lại
        btnRestart.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }
}