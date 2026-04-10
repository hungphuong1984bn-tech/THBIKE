package com.du.dtc.bike.activity;

import com.du.dtc.bike.R;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.du.dtc.bike.ble.BikeData;

public class TechInfoActivity extends AppCompatActivity {

    private TextView tvVolt, tvCurrent, tvSoc, tvAh, tvCycle, tvAbsAh, tvResistor, tvBmsError, tvBmsCharge;
    private TextView tvTempMotor, tvTempEcu, tvTempFet, tvTempBmsAvg;
    private TextView tvNtc1, tvNtc2, tvNtc3, tvNtc4;
    private TextView tvCellSummary, tvWarningThrottle;
    private TextView tvPcbName, tvPcbFw, tvPcbVin, tvPcbOdo, tvPcbError;
    private TextView tvPcbThrottle, tvPcbAdc1, tvPcbAdc2, tvPcbIdleOff;
    private GridLayout gridCells;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable;

    // Ngưỡng cảnh báo
    private static final double CELL_DIFF_IGNORE = 0.04;
    private static final double CELL_DIFF_DANGER = 0.1;
    private static final double THROTTLE_NOISE_THRESHOLD = 0.05;
    private static final double TEMP_HIGH = 45.0;
    private static final double TEMP_DANGER = 60.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tech_info);

        // Nút quay lại
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // BMS
        tvVolt = findViewById(R.id.tv_bms_volt);
        tvCurrent = findViewById(R.id.tv_bms_current);
        tvSoc = findViewById(R.id.tv_bms_soc);
        tvAh = findViewById(R.id.tv_bms_ah);
        tvCycle = findViewById(R.id.tv_bms_cycle);
        tvAbsAh = findViewById(R.id.tv_bms_abs_ah);
        tvResistor = findViewById(R.id.tv_bms_resistor);
        tvBmsError = findViewById(R.id.tv_bms_error);
        tvBmsCharge = findViewById(R.id.tv_bms_charge);

        // Temps
        tvTempMotor = findViewById(R.id.tv_temp_motor);
        tvTempEcu = findViewById(R.id.tv_temp_ecu);
        tvTempFet = findViewById(R.id.tv_temp_fet);
        tvTempBmsAvg = findViewById(R.id.tv_temp_bms_avg);
        tvNtc1 = findViewById(R.id.tv_temp_ntc1);
        tvNtc2 = findViewById(R.id.tv_temp_ntc2);
        tvNtc3 = findViewById(R.id.tv_temp_ntc3);
        tvNtc4 = findViewById(R.id.tv_temp_ntc4);

        tvPcbName = findViewById(R.id.tv_pcb_name);
        tvPcbFw = findViewById(R.id.tv_pcb_fw);
        tvPcbVin = findViewById(R.id.tv_pcb_vin);
        tvPcbOdo = findViewById(R.id.tv_pcb_odo);
        tvPcbError = findViewById(R.id.tv_pcb_error);
        tvPcbThrottle = findViewById(R.id.tv_pcb_throttle);
        tvPcbAdc1 = findViewById(R.id.tv_pcb_adc1);
        tvPcbAdc2 = findViewById(R.id.tv_pcb_adc2);
        tvPcbIdleOff = findViewById(R.id.tv_pcb_idle_off);
        tvCellSummary = findViewById(R.id.tv_cell_summary);
        tvWarningThrottle = findViewById(R.id.tv_warning_throttle);
        gridCells = findViewById(R.id.grid_cells);

        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI(MainActivity.globalBikeData);
                handler.postDelayed(this, 1000); // Cập nhật mỗi giây
            }
        };
        handler.post(updateRunnable);
    }

    private void updateUI(BikeData data) {
        if (data == null)
            return;

        // 1. NHÓM BMS (Đổ từng thông số vào Table)
        tvVolt.setText(String.format("%.2f V", data.voltage));
        tvCurrent.setText(String.format("%.2f A", data.current));
        tvSoc.setText(String.format("%.1f%%", data.soc));
        tvAh.setText(String.format("%.2f Ah", data.currentAh));
        tvCycle.setText(String.format("%.1f", data.cycles));
        tvAbsAh.setText(String.format("%.1f Ah", data.absAh));
        tvResistor.setText(formatTemp(data.tempBalanceReg));

        // Trạng thái sạc / xả
        String chargeState = "";
        if (data.current > 0)
            chargeState = "Đang sạc";
        else
            chargeState = "Đang xả";
        tvBmsCharge.setText(chargeState);

        // Lỗi BMS (Màu đỏ nếu có)
        if (data.bmsError > 0) {
            tvBmsError.setText(Html.fromHtml("<font color='#FF4444'>Có lỗi</font>", Html.FROM_HTML_MODE_COMPACT));
        } else {
            tvBmsError.setText("Không");
            tvBmsError.setTextColor(Color.parseColor("#EBEBF5")); // Trắng xám
        }

        // 2. NHIỆT ĐỘ HỆ THỐNG
        // formatTemp() tạo màu sắc nếu vượt ngưỡng nguy hiểm
        tvTempMotor.setText(Html.fromHtml(formatTemp(data.tempMotor), Html.FROM_HTML_MODE_COMPACT));
        tvTempEcu.setText(Html.fromHtml(formatTemp(data.tempController), Html.FROM_HTML_MODE_COMPACT));
        tvTempFet.setText(Html.fromHtml(formatTemp(data.tempFet), Html.FROM_HTML_MODE_COMPACT));
        tvTempBmsAvg.setText(Html.fromHtml(formatTemp(data.tempBms), Html.FROM_HTML_MODE_COMPACT));

        // NTC
        tvNtc1.setText(Html.fromHtml(formatTemp(data.tempPin1), Html.FROM_HTML_MODE_COMPACT));
        tvNtc2.setText(Html.fromHtml(formatTemp(data.tempPin2), Html.FROM_HTML_MODE_COMPACT));
        tvNtc3.setText(Html.fromHtml(formatTemp(data.tempPin3), Html.FROM_HTML_MODE_COMPACT));
        tvNtc4.setText(Html.fromHtml(formatTemp(data.tempPin4), Html.FROM_HTML_MODE_COMPACT));

        // 3. CELL PIN
        tvCellSummary
                .setText(String.format("Lệch: %.2f V (Min: %.2f | Max: %.2f)", data.cellDiff, data.vMin, data.vMax));
        gridCells.removeAllViews();
        for (int i = 0; i < data.cellVoltages.size(); i++) {
            double v = data.cellVoltages.get(i);
            TextView tv = new TextView(this);
            tv.setText(String.format("%d\n%.2fV", i + 1, v));
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(4, 12, 4, 12);
            tv.setTextSize(12f);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(4, 4, 4, 4);
            tv.setLayoutParams(lp);

            // Style mặc định cho Dark Mode
            tv.setBackgroundColor(Color.parseColor("#2C2C2E"));
            tv.setTextColor(Color.parseColor("#EBEBF5"));

            if (data.cellDiff > CELL_DIFF_IGNORE) {
                if (v == data.vMax) {
                    tv.setTextColor(Color.CYAN);
                    tv.setTypeface(null, Typeface.BOLD);
                } else if (v == data.vMin) {
                    tv.setTextColor(Color.YELLOW);
                    tv.setTypeface(null, Typeface.BOLD);
                }

                if (data.cellDiff >= CELL_DIFF_DANGER && (v == data.vMax || v == data.vMin)) {
                    tv.setBackgroundColor(Color.parseColor("#8E0000")); // Đỏ tối
                    tv.setTextColor(Color.WHITE);
                }
            }
            gridCells.addView(tv);
        }

        // 4. VẬN HÀNH & PCB
        // Tính toán độ lệch giữa 2 đường tín hiệu ADC của tay ga
        double diffAdc = Math.abs(data.adc1 - data.adc2);

        if (data.pcbState == BikeData.PCB_STATE_OFF) {
            // TRẠNG THÁI NGHỈ: Chỉ kiểm tra sự đồng bộ giữa 2 cảm biến ADC
            if (diffAdc > THROTTLE_NOISE_THRESHOLD) {
                tvWarningThrottle.setVisibility(View.VISIBLE);
                tvWarningThrottle.setText("⚠️ Cảnh báo: Lệch tín hiệu cảm biến tay ga (ADC)");
                tvWarningThrottle.setBackgroundColor(Color.parseColor("#F57C00")); // Màu cam
            } else if (data.adc2 > 0.95 && data.speed == 0) {
                // Cảnh báo nguy hiểm: Tay ga đang vặn cao mà xe chưa chạy
                tvWarningThrottle.setVisibility(View.VISIBLE);
                tvWarningThrottle.setText("⚠️ CHÚ Ý: Tín hiệu tay ga cao bất thường!");
                tvWarningThrottle.setBackgroundColor(Color.parseColor("#D32F2F")); // Màu đỏ
            } else {
                tvWarningThrottle.setVisibility(View.GONE);
            }
        } else {
            // TRẠNG THÁI ĐANG VẬN HÀNH (pcbState > 0): Logic đầy đủ
            double diffThrottle = Math.max(Math.abs(data.throttle - data.adc1), Math.abs(data.throttle - data.adc2));

            if (data.throttle > 0.95 && data.speed == 0) {
                // Cảnh báo nguy hiểm: Tay ga đang vặn cao mà xe chưa chạy
                tvWarningThrottle.setVisibility(View.VISIBLE);
                tvWarningThrottle.setText("⚠️ CHÚ Ý: Tín hiệu tay ga cao bất thường!");
                tvWarningThrottle.setBackgroundColor(Color.parseColor("#D32F2F")); // Màu đỏ
            } else if (diffThrottle > THROTTLE_NOISE_THRESHOLD) {
                // Cảnh báo nhiễu: Lệch giữa tay ga thực tế và giá trị điều khiển
                tvWarningThrottle.setVisibility(View.VISIBLE);
                tvWarningThrottle.setText("⚠️ Cảnh báo: Có dấu hiệu nhiễu tín hiệu tay ga.");
                tvWarningThrottle.setBackgroundColor(Color.parseColor("#F57C00")); // Màu cam
            } else {
                tvWarningThrottle.setVisibility(View.GONE);
            }
        }

        // Đổ dữ liệu vào bảng TableLayout mới
        tvPcbName.setText(data.name);
        String fwDisplay = String.format("v%s-%s", data.fw,
                data.fwHash.substring(0, Math.min(data.fwHash.length(), 7)));
        tvPcbFw.setText(fwDisplay);
        tvPcbVin.setText(data.vin);
        tvPcbOdo.setText(String.format("%.1f km", data.odo));
        tvPcbThrottle.setText(String.format("%.2f", data.throttle));
        tvPcbAdc1.setText(String.format("%.2f", data.adc1));
        tvPcbAdc2.setText(String.format("%.2f", data.adc2));
        tvPcbIdleOff.setText(data.idleOff ? "Bật" : "Tắt");

        // (Tuỳ chọn) Nếu bạn muốn chữ Bật màu Xanh, Tắt màu Xám cho trực quan thì dùng
        // đoạn này:
        if (data.idleOff) {
            tvPcbIdleOff.setTextColor(Color.parseColor("#4CAF50")); // Màu xanh lá
        } else {
            tvPcbIdleOff.setTextColor(Color.parseColor("#9E9E9E")); // Màu xám
        }

        if (data.pcbError > 0) {
            tvPcbError.setText(
                    Html.fromHtml("<font color='#FF4444'>Có lỗi</font>", Html.FROM_HTML_MODE_COMPACT));
        } else {
            tvPcbError.setText("Không");
            tvPcbError.setTextColor(Color.parseColor("#EBEBF5"));
        }
    }

    private String formatTemp(double temp) {
        String f = String.format("%.1f°C", temp);
        if (temp > TEMP_DANGER)
            return "<font color='#FFFFFF' style='background-color:#CC0000'><b> " + f + " </b></font>";
        if (temp > TEMP_HIGH)
            return "<font color='#FF4444'><b>" + f + "</b></font>";
        return f;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateRunnable);
    }
}