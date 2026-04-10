package com.du.dtc.bike.activity;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.du.dtc.bike.R;
import com.du.dtc.bike.db.BikeDatabase;
import com.du.dtc.bike.db.BikeLogEntity;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HistoryActivity extends AppCompatActivity {

    // ── Biểu đồ nhiệt độ ──
    private LineChart chartTemp;
    private boolean[] tempVisible = { true, true, true, true, true, true, true, true };
    private LineData tempData;

    // ── Biểu đồ điện & tốc độ ──
    private LineChart chartPower;
    private boolean[] powerVisible = { true, true, true, true };
    private LineData powerData;

    // ── Biểu đồ cell ──
    private LineChart chartCells;

    // ── UI Components ──
    private ProgressBar progressBar;
    private TextView tvRecordCount;
    private TextView tvSelectDate, tvSelectStartTime, tvSelectEndTime;

    // ── Time Filter Variables ──
    private Calendar filterDate = Calendar.getInstance(); // Ngày đang chọn (Mặc định: Hôm nay)
    private Integer startHour = null, startMinute = null;
    private Integer endHour = null, endMinute = null;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Màu sắc cho các series
    private static final int[] TEMP_COLORS = {
            Color.parseColor("#FF6B6B"), // BalReg
            Color.parseColor("#FFA500"), // FET
            Color.parseColor("#4ECDC4"), // Pin1
            Color.parseColor("#95E1D3"), // Pin2
            Color.parseColor("#A8E6CF"), // Pin3
            Color.parseColor("#DCEDC1"), // Pin4
            Color.parseColor("#C678DD"), // Motor
            Color.parseColor("#E06C75"), // Controller
    };
    private static final String[] TEMP_LABELS = {
            "BalReg", "FET", "Pin1", "Pin2", "Pin3", "Pin4", "Motor", "Controller"
    };

    // Legend IDs
    private int[] tempLegendIds;
    private int[] powerLegendIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        tvRecordCount = findViewById(R.id.tv_record_count);

        chartTemp = findViewById(R.id.chart_temp);
        chartPower = findViewById(R.id.chart_power);
        chartCells = findViewById(R.id.chart_cells);

        // Nút chọn thời gian
        tvSelectDate = findViewById(R.id.tv_select_date);
        tvSelectStartTime = findViewById(R.id.tv_select_start_time);
        tvSelectEndTime = findViewById(R.id.tv_select_end_time);

        setupChartAppearance(chartTemp, "°C");
        setupChartAppearance(chartPower, "");
        setupChartAppearance(chartCells, "V");

        setupLegends();
        setupTimePickers();
        updateTimeFilterUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Luôn reload lại data mỗi lần Activity được đưa lên màn hình
        loadFromDatabase();
    }

    // ─────────────────────────────────────────────
    // Logic Bộ lọc Thời gian (Date/Time Pickers)
    // ─────────────────────────────────────────────
    private void setupTimePickers() {
        tvSelectDate.setOnClickListener(v -> {
            DatePickerDialog dpd = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                filterDate.set(year, month, dayOfMonth);
                updateTimeFilterUI();
                loadFromDatabase();
            }, filterDate.get(Calendar.YEAR), filterDate.get(Calendar.MONTH), filterDate.get(Calendar.DAY_OF_MONTH));
            dpd.getDatePicker().setMaxDate(System.currentTimeMillis()); // Không cho chọn tương lai
            dpd.show();
        });

        tvSelectStartTime.setOnClickListener(v -> {
            int h = startHour != null ? startHour : 0;
            int m = startMinute != null ? startMinute : 0;
            TimePickerDialog tpd = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                startHour = hourOfDay;
                startMinute = minute;
                validateAndApplyTimeFilter();
            }, h, m, true);
            tpd.show();
        });

        tvSelectEndTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            int h = endHour != null ? endHour : now.get(Calendar.HOUR_OF_DAY);
            int m = endMinute != null ? endMinute : now.get(Calendar.MINUTE);
            TimePickerDialog tpd = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                endHour = hourOfDay;
                endMinute = minute;
                validateAndApplyTimeFilter();
            }, h, m, true);
            tpd.show();
        });
    }

    private void validateAndApplyTimeFilter() {
        Calendar now = Calendar.getInstance();
        boolean isToday = filterDate.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                filterDate.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);

        // Chặn chọn giờ bắt đầu trong tương lai (nếu là hôm nay)
        if (isToday && startHour != null) {
            if (startHour > now.get(Calendar.HOUR_OF_DAY)
                    || (startHour == now.get(Calendar.HOUR_OF_DAY) && startMinute > now.get(Calendar.MINUTE))) {
                Toast.makeText(this, "Giờ bắt đầu không được lớn hơn hiện tại", Toast.LENGTH_SHORT).show();
                startHour = now.get(Calendar.HOUR_OF_DAY);
                startMinute = now.get(Calendar.MINUTE);
            }
        }

        // Chặn chọn giờ kết thúc trong tương lai (nếu là hôm nay)
        if (isToday && endHour != null) {
            if (endHour > now.get(Calendar.HOUR_OF_DAY)
                    || (endHour == now.get(Calendar.HOUR_OF_DAY) && endMinute > now.get(Calendar.MINUTE))) {
                Toast.makeText(this, "Giờ kết thúc không được lớn hơn hiện tại", Toast.LENGTH_SHORT).show();
                endHour = now.get(Calendar.HOUR_OF_DAY);
                endMinute = now.get(Calendar.MINUTE);
            }
        }

        // Chặn giờ kết thúc nhỏ hơn giờ bắt đầu
        if (startHour != null && endHour != null) {
            int startTotalMins = startHour * 60 + startMinute;
            int endTotalMins = endHour * 60 + endMinute;
            if (endTotalMins < startTotalMins) {
                Toast.makeText(this, "Giờ kết thúc phải sau giờ bắt đầu", Toast.LENGTH_SHORT).show();
                endHour = startHour;
                endMinute = startMinute;
            }
        }

        updateTimeFilterUI();
        loadFromDatabase();
    }

    private void updateTimeFilterUI() {
        tvSelectDate.setText(dateFormat.format(filterDate.getTime()));

        if (startHour == null) {
            tvSelectStartTime.setText("00:00");
        } else {
            tvSelectStartTime.setText(String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute));
        }

        if (endHour == null) {
            tvSelectEndTime.setText("Hiện tại");
        } else {
            tvSelectEndTime.setText(String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute));
        }
    }

    // Tính toán mốc thời gian dạng ms để query Database
    private long[] getFilterTimestamps() {
        Calendar cStart = (Calendar) filterDate.clone();
        cStart.set(Calendar.HOUR_OF_DAY, startHour != null ? startHour : 0);
        cStart.set(Calendar.MINUTE, startMinute != null ? startMinute : 0);
        cStart.set(Calendar.SECOND, 0);

        Calendar cEnd = (Calendar) filterDate.clone();
        if (endHour != null) {
            cEnd.set(Calendar.HOUR_OF_DAY, endHour);
            cEnd.set(Calendar.MINUTE, endMinute);
            cEnd.set(Calendar.SECOND, 59);
        } else {
            Calendar now = Calendar.getInstance();
            boolean isToday = filterDate.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    filterDate.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR);
            if (isToday) {
                cEnd = now; // Mặc định giờ hiện tại
            } else {
                cEnd.set(Calendar.HOUR_OF_DAY, 23);
                cEnd.set(Calendar.MINUTE, 59);
                cEnd.set(Calendar.SECOND, 59);
            }
        }

        return new long[] { cStart.getTimeInMillis(), cEnd.getTimeInMillis() };
    }

    // ─────────────────────────────────────────────
    // Load dữ liệu từ Room DB theo mốc thời gian
    // ─────────────────────────────────────────────
    private void loadFromDatabase() {
        progressBar.setVisibility(View.VISIBLE);
        long[] ts = getFilterTimestamps();
        long startTs = ts[0];
        long endTs = ts[1];

        executor.execute(() -> {
            // LƯU Ý: Yêu cầu cập nhật BikeLogDao thêm hàm getLogsByTimeRange (OrderBy ASC)
            List<BikeLogEntity> logs = BikeDatabase.getDatabase(this)
                    .bikeLogDao()
                    .getLogsByTimeRange(startTs, endTs);

            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                tvRecordCount.setText(logs.size() + " bản ghi");

                if (logs.isEmpty()) {
                    chartTemp.clear();
                    chartPower.clear();
                    chartCells.clear();
                    return;
                }

                buildTempChart(logs);
                buildPowerChart(logs);
                buildCellChart(logs);
            });
        });
    }

    // ─────────────────────────────────────────────
    // UI Setups & Builders (Giữ nguyên logic cũ)
    // ─────────────────────────────────────────────
    private void setupLegends() {
        tempLegendIds = new int[] {
                R.id.legend_balreg, R.id.legend_fet, R.id.legend_pin1, R.id.legend_pin2,
                R.id.legend_pin3, R.id.legend_pin4, R.id.legend_motor, R.id.legend_ctrl
        };
        for (int i = 0; i < tempLegendIds.length; i++) {
            final int idx = i;
            LinearLayout legend = findViewById(tempLegendIds[i]);
            if (legend != null)
                legend.setOnClickListener(v -> toggleTempSeries(idx));
        }

        powerLegendIds = new int[] {
                R.id.legend_voltage, R.id.legend_current, R.id.legend_speed, R.id.legend_soc
        };
        for (int i = 0; i < powerLegendIds.length; i++) {
            final int idx = i;
            LinearLayout legend = findViewById(powerLegendIds[i]);
            if (legend != null)
                legend.setOnClickListener(v -> togglePowerSeries(idx));
        }
    }

    private void setupChartAppearance(LineChart chart, String yUnit) {
        chart.setBackgroundColor(Color.parseColor("#1A1F2E"));
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setDrawBorders(false);
        chart.setPinchZoom(true);
        chart.setDoubleTapToZoomEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setNoDataText("Chưa có dữ liệu trong khung giờ này");
        chart.setNoDataTextColor(Color.parseColor("#888EA8"));

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.parseColor("#888EA8"));
        xAxis.setTextSize(9f);
        xAxis.setDrawGridLines(true);
        xAxis.setGridColor(Color.parseColor("#2A2F3E"));
        xAxis.setAxisLineColor(Color.parseColor("#3A3F4E"));
        xAxis.setGranularity(1f);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

            @Override
            public String getFormattedValue(float value) {
                return sdf.format(new Date((long) value));
            }
        });

        YAxis left = chart.getAxisLeft();
        left.setTextColor(Color.parseColor("#888EA8"));
        left.setTextSize(9f);
        left.setDrawGridLines(true);
        left.setGridColor(Color.parseColor("#2A2F3E"));
        left.setAxisLineColor(Color.parseColor("#3A3F4E"));
        if (!yUnit.isEmpty()) {
            left.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.format(Locale.getDefault(), "%.1f%s", value, yUnit);
                }
            });
        }

        chart.getAxisRight().setEnabled(false);
    }

    private void buildTempChart(List<BikeLogEntity> logs) {
        @SuppressWarnings("unchecked")
        List<Entry>[] series = new List[8];
        for (int i = 0; i < 8; i++)
            series[i] = new ArrayList<>();

        for (BikeLogEntity e : logs) {
            float x = e.timestamp;
            series[0].add(new Entry(x, (float) e.tempBalanceReg));
            series[1].add(new Entry(x, (float) e.tempFet));
            series[2].add(new Entry(x, (float) e.tempPin1));
            series[3].add(new Entry(x, (float) e.tempPin2));
            series[4].add(new Entry(x, (float) e.tempPin3));
            series[5].add(new Entry(x, (float) e.tempPin4));
            series[6].add(new Entry(x, (float) e.tempMotor));
            series[7].add(new Entry(x, (float) e.tempController));
        }

        List<LineDataSet> datasets = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            LineDataSet ds = makeLineDataSet(series[i], TEMP_LABELS[i], TEMP_COLORS[i]);
            ds.setVisible(tempVisible[i]); // Apply current visibility state
            datasets.add(ds);
        }

        tempData = new LineData(new ArrayList<>(datasets));
        chartTemp.setData(tempData);
        chartTemp.invalidate();
    }

    private void buildPowerChart(List<BikeLogEntity> logs) {
        List<Entry> voltageEntries = new ArrayList<>();
        List<Entry> currentEntries = new ArrayList<>();
        List<Entry> speedEntries = new ArrayList<>();
        List<Entry> socEntries = new ArrayList<>();

        for (BikeLogEntity e : logs) {
            float x = e.timestamp;
            voltageEntries.add(new Entry(x, (float) e.voltage));
            currentEntries.add(new Entry(x, (float) e.current));
            speedEntries.add(new Entry(x, (float) e.speed));
            socEntries.add(new Entry(x, (float) e.soc));
        }

        LineDataSet dsVoltage = makeLineDataSet(voltageEntries, "Điện áp (V)", Color.parseColor("#61AFEF"));
        dsVoltage.setVisible(powerVisible[0]);

        LineDataSet dsCurrent = makeLineDataSet(currentEntries, "Dòng (A)", Color.parseColor("#E5C07B"));
        dsCurrent.setVisible(powerVisible[1]);

        LineDataSet dsSpeed = makeLineDataSet(speedEntries, "Tốc độ (km/h)", Color.parseColor("#98C379"));
        dsSpeed.setVisible(powerVisible[2]);

        LineDataSet dsSoc = makeLineDataSet(socEntries, "SOC (%)", Color.parseColor("#56B6C2"));
        dsSoc.setVisible(powerVisible[3]);

        powerData = new LineData(dsVoltage, dsCurrent, dsSpeed, dsSoc);
        chartPower.setData(powerData);
        chartPower.invalidate();
    }

    private void buildCellChart(List<BikeLogEntity> logs) {
        List<Entry> maxEntries = new ArrayList<>();
        List<Entry> minEntries = new ArrayList<>();
        List<Entry> diffEntries = new ArrayList<>();

        for (BikeLogEntity e : logs) {
            if (e.cellVoltages == null || e.cellVoltages.isEmpty())
                continue;
            float x = e.timestamp;

            double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
            for (double v : e.cellVoltages) {
                if (v < min)
                    min = v;
                if (v > max)
                    max = v;
            }
            double diff = max - min;

            maxEntries.add(new Entry(x, (float) max));
            minEntries.add(new Entry(x, (float) min));
            diffEntries.add(new Entry(x, (float) diff));
        }

        if (maxEntries.isEmpty()) {
            chartCells.clear();
            return;
        }

        LineDataSet dsMax = makeLineDataSet(maxEntries, "Vmax", Color.parseColor("#98C379"));
        dsMax.setLineWidth(1.8f);

        LineDataSet dsMin = makeLineDataSet(minEntries, "Vmin", Color.parseColor("#FF6B6B"));
        dsMin.setLineWidth(1.8f);

        LineDataSet dsDiff = makeLineDataSet(diffEntries, "Lệch (Diff)", Color.parseColor("#E5C07B"));
        dsDiff.setLineWidth(2f);
        dsDiff.setFillColor(Color.parseColor("#40E5C07B"));
        dsDiff.setDrawFilled(true);
        dsDiff.setFillAlpha(60);

        LineData cellData = new LineData(dsMax, dsMin, dsDiff);
        chartCells.setData(cellData);
        chartCells.invalidate();
    }

    private LineDataSet makeLineDataSet(List<Entry> entries, String label, int color) {
        LineDataSet ds = new LineDataSet(entries, label);
        ds.setColor(color);
        ds.setLineWidth(1.5f);
        ds.setDrawCircles(false);
        ds.setDrawValues(false);
        ds.setMode(LineDataSet.Mode.LINEAR);
        ds.setHighlightLineWidth(1f);
        ds.setHighLightColor(Color.parseColor("#80FFFFFF"));
        return ds;
    }

    private void toggleTempSeries(int index) {
        if (tempData == null)
            return;
        tempVisible[index] = !tempVisible[index];
        LineDataSet ds = (LineDataSet) tempData.getDataSetByIndex(index);
        if (ds != null)
            ds.setVisible(tempVisible[index]);

        LinearLayout legend = findViewById(tempLegendIds[index]);
        if (legend != null)
            legend.setAlpha(tempVisible[index] ? 1f : 0.35f);
        chartTemp.invalidate();
    }

    private void togglePowerSeries(int index) {
        if (powerData == null)
            return;
        powerVisible[index] = !powerVisible[index];
        LineDataSet ds = (LineDataSet) powerData.getDataSetByIndex(index);
        if (ds != null)
            ds.setVisible(powerVisible[index]);

        LinearLayout legend = findViewById(powerLegendIds[index]);
        if (legend != null)
            legend.setAlpha(powerVisible[index] ? 1f : 0.35f);
        chartPower.invalidate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}