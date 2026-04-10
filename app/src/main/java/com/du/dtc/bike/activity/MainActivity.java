package com.du.dtc.bike.activity;

import com.du.dtc.bike.R;
import android.view.ViewGroup;
import java.util.Set;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.Uri;

import com.du.dtc.bike.ble.BikeBleLib;
import com.du.dtc.bike.ble.BikeData;
import com.du.dtc.bike.ble.DataParser;
import com.du.dtc.bike.BuildConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import com.du.dtc.bike.log.BleDebugLogger;
import android.widget.ImageButton;
import com.du.dtc.bike.BikeBackgroundService;
import android.widget.ImageView;
import com.du.dtc.bike.BuildConfig;
import com.du.dtc.bike.ble.BikeBleControl;

public class MainActivity extends AppCompatActivity {

    public static BikeBleLib bikeBleLib;
    private List<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private ArrayAdapter<String> deviceListAdapter;
    private AlertDialog scanDialog;
    private int speedClickCount = 0;
    private long lastSpeedClickTime = 0;

    // UI Elements
    private TextView tvBikeName, tvBikeModel, tvBikeFw;
    private TextView tvBattery, tvDriveMode, tvEstRange;
    private TextView tvSpeed, tvCurrent, tvOdo, tvVoltage;
    private TextView tvTempBms, tvTempFet, tvTempMotor, tvTempAcu;
    private TextView tvTempPin1, tvTempPin2, tvTempPin3, tvTempPin4;
    private TextView tvCellDiff, tvVmin, tvVmax;
    private TextView iconSignal, iconLeft, iconRight, iconHeadlight;
    private TextView tvCycles;
    private TextView tvLabelFunction;
    private View btnTechInfo;
    private View btnMenu; // Nút |||
    private ImageView btnFunction, btnFind, btnAdvanced, btnHistory;
    private TextView tvLockStatus;

    private static final int PERMISSION_REQUEST_CODE = 123;

    public static BikeData globalBikeData = new BikeData();

    private AlphaAnimation blinkAnimation;

    private static final String PREF_NAME = "BikeAppPrefs";
    private static final String KEY_MAC = "saved_mac_address";

    private BikeBackgroundService bikeService;
    private boolean isBound = false;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BikeBackgroundService.LocalBinder binder = (BikeBackgroundService.LocalBinder) service;
            bikeService = binder.getService();
            isBound = true;
            bikeBleLib = bikeService.getBikeBleLib();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    private final BroadcastReceiver dataUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isBound && bikeService != null) {
                globalBikeData = bikeService.globalBikeData; // Đồng bộ dữ liệu sang biến static cho TechInfoActivity
                                                             // dùng ké
                runOnUiThread(() -> updateUI(globalBikeData));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        setContentView(R.layout.activity_main);
        seekRegen = findViewById(R.id.seek_regen);
tvRegen = findViewById(R.id.tv_regen);

// tránh crash nếu chưa có view
if (seekRegen != null) {

    seekRegen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {

            if (tvRegen != null) {
                tvRegen.setText("Regen: " + value);
            }

            sendCommand("REGEN_" + value);
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    });
}

        initViews();
        resetUI(); // Đưa về trạng thái -- ngay khi mở app

        // Khởi tạo tạm thời nếu service chưa bind
        bikeBleLib = new BikeBleLib(this);

        checkAndRequestBatteryOptimization();

        // Thiết lập sự kiện cho nút Menu (|||)
        btnMenu.setOnClickListener(v -> checkPermissionsAndShowScanDialog());

        // 2. Nhấn giữ (Long Click): Xóa thiết bị đã lưu và ngắt kết nối
        btnMenu.setOnLongClickListener(v -> btnMenuLongClick());

        if (BleDebugLogger.DEBUG) {
            tvSpeed.setOnClickListener(v -> tvSpeedClick());
        }

        btnTechInfo.setOnClickListener(v -> btnTechInfoClick());

        btnFunction.setOnClickListener(v -> onFunctionButtonClick());

        btnFind.setOnClickListener(v -> findBike(v));

        btnAdvanced.setOnClickListener(v -> btnAdvancedClick());

        btnHistory.setOnClickListener(v -> btnHistoryClick());

        AlphaAnimation blinkAnimation = new AlphaAnimation(1.0f, 0.0f);
        blinkAnimation.setDuration(500); // 0.5 giây mỗi lần nháy
        blinkAnimation.setInterpolator(new LinearInterpolator());
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        blinkAnimation.setRepeatMode(Animation.REVERSE);

        // 3. GỌI HÀM TỰ ĐỘNG KẾT NỐI NGAY KHI MỞ APP
        checkPermissionsAndAutoConnect();
    }

    private boolean btnMenuLongClick() {
        saveDeviceMac(null); // Xóa MAC trong bộ nhớ
        if (bikeService != null)
            bikeService.disconnect(); // Ngắt kết nối hiện tại qua service
        else if (bikeBleLib != null)
            bikeBleLib.disconnect();
        resetUI();
        Toast.makeText(MainActivity.this, "Đã quên xe. Hãy quét để kết nối xe mới!", Toast.LENGTH_LONG).show();
        checkPermissionsAndShowScanDialog(); // Bật popup quét lại
        return true;
    }

    private void btnHistoryClick() {
        startActivity(new Intent(MainActivity.this, HistoryActivity.class));
    }

    private void tvSpeedClick() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSpeedClickTime < 500) {
            speedClickCount++;
        } else {
            speedClickCount = 1;
        }
        lastSpeedClickTime = currentTime;

        if (speedClickCount == 3) {
            speedClickCount = 0; // Reset lại

            // Gọi hàm từ class mới
            com.du.dtc.bike.log.BleDebugLogger.exportAndShareLogs(MainActivity.this);
        }
    }

    private void btnTechInfoClick() {
        android.content.Intent intent = new android.content.Intent(MainActivity.this, TechInfoActivity.class);
        startActivity(intent);
    }

    private void btnAdvancedClick() {
        btnTechInfoClick();
    }

    private void findBike(View v) {
        // 1 & 2. Kiểm tra xe đã kết nối và quét xong dịch vụ chưa
        if (bikeBleLib == null || bikeBleLib.bikeControl == null) {
            Toast.makeText(MainActivity.this, "Xe chưa kết nối hoặc chưa sẵn sàng!", Toast.LENGTH_SHORT).show();
            return; // Dừng lại ngay
        }

        // 3. Kiểm tra quyền điều khiển (Đã xác thực session token chưa)
        if (!bikeBleLib.bikeControl.canControl()) {
            Toast.makeText(MainActivity.this, "🔒 Từ chối: Bạn chưa quẹt thẻ hoặc App chính chưa xác thực!",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // 4. Nếu qua hết các vòng bảo vệ -> Bắn lệnh!
        bikeBleLib.bikeControl.findBike();
        Toast.makeText(MainActivity.this, "🔊 Đang gửi lệnh tìm xe...", Toast.LENGTH_SHORT).show();

        // Tùy chọn (UX): Làm nút nháy mờ một cái để người dùng biết đã bấm
        v.setAlpha(0.5f);
        v.animate().alpha(1.0f).setDuration(200).start();
    }

    private void updateFunctionButtonUI(BikeData data) {
        String mac = getSavedDeviceMac();

        // NẾU CHƯA CÓ MAC: Bắt buộc đè giao diện XML mặc định thành trạng thái "Quét
        // xe"
        if (mac == null) {
            btnFunction.setImageResource(R.drawable.connect);
            tvLabelFunction.setText("Quét xe");
            return;
        }

        // TRƯỜNG HỢP 0: Có MAC nhưng chưa kết nối sóng BLE
        if (bikeBleLib == null || !bikeBleLib.isConnected()) {
            btnFunction.setImageResource(R.drawable.blue);
            tvLabelFunction.setText("Kết nối");
            return;
        }

        // TRƯỜNG HỢP 3: Đã kết nối và đã có quyền điều khiển
        if (data.pcbState == BikeData.PCB_STATE_OFF) {
            if (data.isLocked) {
                btnFunction.setImageResource(R.drawable.unlock); // Xe đang khóa -> Hiện nút Mở khóa
                tvLabelFunction.setText("Mở khóa");
            } else {
                btnFunction.setImageResource(R.drawable.lock); // Xe đang tắt nhưng ko khóa -> Hiện nút Khóa
                tvLabelFunction.setText("Khóa xe");
            }
        } else {
            // Xe đang mở máy -> Hiện nút Khóa (Bấm vào sẽ tắt máy rồi khóa)
            btnFunction.setImageResource(R.drawable.lock);
            tvLabelFunction.setText("Khóa xe");
        }
    }

    private void onFunctionButtonClick() {

        // Đọc label hiện tại do updateFunctionButtonUI đã set -> dispatch đúng hành
        // động
        String label = tvLabelFunction.getText().toString();

        // 👉 Đưa biến mac ra ngoài để dùng chung cho tất cả các case, tránh lỗi cú pháp
        // Java
        String mac = getSavedDeviceMac();

        switch (label) {
            case "Quét xe":
                // mac == null: chưa lưu thiết bị nào
                checkPermissionsAndShowScanDialog();
                break;

            case "Xác thực":
            case "Kết nối":
                // Có token nhưng chưa kết nối -> thử kết nối lại
                if (mac != null && bikeService != null) {
                    bikeService.connectToDevice(mac);
                    Toast.makeText(this, "Đang kết nối lại...", Toast.LENGTH_SHORT).show();
                }
                break;

            case "Mở khóa":
                Toast.makeText(this, "Tính năng này sẽ có trong tương lai", Toast.LENGTH_SHORT).show();
                break;

            case "Khóa xe":
                Toast.makeText(this, "Tính năng này sẽ có trong tương lai", Toast.LENGTH_SHORT).show();
                break;

            default:
                // Trạng thái không xác định -> fallback quét xe
                checkPermissionsAndShowScanDialog();
                break;
        }
    }

    private void initViews() {
        // Ánh xạ các TextView (giữ nguyên id của bạn)
        tvBikeName = findViewById(R.id.tv_bike_name);
        tvBikeModel = findViewById(R.id.tv_bike_model);
        tvBikeFw = findViewById(R.id.tv_bike_fw);
        iconSignal = findViewById(R.id.icon_signal);
        iconLeft = findViewById(R.id.icon_turn_left);
        iconRight = findViewById(R.id.icon_turn_right);
        tvBattery = findViewById(R.id.tv_battery);
        tvDriveMode = findViewById(R.id.tv_drive_mode);
        tvEstRange = findViewById(R.id.tv_est_range);
        tvSpeed = findViewById(R.id.tv_speed);
        tvCurrent = findViewById(R.id.tv_current);
        tvOdo = findViewById(R.id.tv_odo);
        tvVoltage = findViewById(R.id.tv_voltage);
        tvCycles = findViewById(R.id.tv_cycles);
        btnTechInfo = findViewById(R.id.btn_tech_info);
        tvTempBms = findViewById(R.id.tv_temp_bms);
        tvTempFet = findViewById(R.id.tv_temp_fet);
        tvTempMotor = findViewById(R.id.tv_temp_motor);
        tvTempAcu = findViewById(R.id.tv_temp_acu);
        tvTempPin1 = findViewById(R.id.tv_temp_pin1);
        tvTempPin2 = findViewById(R.id.tv_temp_pin2);
        tvTempPin3 = findViewById(R.id.tv_temp_pin3);
        tvTempPin4 = findViewById(R.id.tv_temp_pin4);
        tvCellDiff = findViewById(R.id.tv_cell_diff);
        tvVmin = findViewById(R.id.tv_vmin);
        tvVmax = findViewById(R.id.tv_vmax);
        iconHeadlight = findViewById(R.id.icon_light);
        btnFunction = findViewById(R.id.btn_lock);
        btnFind = findViewById(R.id.btn_find);
        btnAdvanced = findViewById(R.id.btn_advanced);
        btnHistory = findViewById(R.id.btn_history);
        tvLabelFunction = findViewById(R.id.tv_label_function);
        tvLockStatus = findViewById(R.id.tv_lock_status);

        blinkAnimation = new AlphaAnimation(1.0f, 0.0f);
        blinkAnimation.setDuration(500);
        blinkAnimation.setInterpolator(new LinearInterpolator());
        blinkAnimation.setRepeatCount(Animation.INFINITE);
        blinkAnimation.setRepeatMode(Animation.REVERSE);

        btnMenu = findViewById(R.id.btn_menu);

        btnTechInfo.setVisibility(View.INVISIBLE);

        // if (!ALLOW_ACTION) {
        // findViewById(R.id.action_buttons_row).setVisibility(View.GONE);
        // }
    }
     <SeekBar
    android:id="@+id/seek_regen"
    android:max="3"
    android:progress="1"/>

    // Yêu cầu 3: Hiển thị -- khi chưa có dữ liệu
    private void resetUI() {
        String placeholder = "--";
        tvBikeName.setText("Chưa kết nối");
        tvBikeModel.setText("Dòng xe: " + placeholder);
        tvBikeFw.setText("Phiên bản: " + placeholder);
        tvBattery.setText(placeholder + "%");
        tvDriveMode.setText(placeholder);
        tvEstRange.setText(placeholder + " KM");
        tvSpeed.setText("0");
        tvCurrent.setText(placeholder + " A");
        tvOdo.setText("ODO: " + placeholder);
        tvCycles.setText("Chu kỳ sạc: " + placeholder);
        tvVoltage.setText("Điện áp: " + placeholder);
        tvTempBms.setText("BMS: " + placeholder);
        tvTempFet.setText("FET: " + placeholder);
        tvTempMotor.setText("Motor: " + placeholder);
        tvTempAcu.setText("Acu: " + placeholder);
        tvTempPin1.setText("P1: " + placeholder);
        tvTempPin2.setText("P2: " + placeholder);
        tvTempPin3.setText("P3: " + placeholder);
        tvTempPin4.setText("P4: " + placeholder);
        tvVmin.setText("Vmin: " + placeholder);
        tvVmax.setText("Vmax: " + placeholder);
        tvCellDiff.setText("Lệch: " + placeholder);
        iconSignal.setText("📶");

        if (tvLockStatus != null) {
            tvLockStatus.setVisibility(View.GONE);
        }

        btnTechInfo.setVisibility(View.GONE);

        // Bổ sung đè hiển thị XML cho nút Function
        if (btnFunction != null && tvLabelFunction != null) {
            btnFunction.setImageResource(R.drawable.connect);
            tvLabelFunction.setText("Xác thực");
        }

        updateFunctionButtonUI(null);
    }

    private void checkPermissionsAndShowScanDialog() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        List<String> missing = new ArrayList<>();
        for (String p : permissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                missing.add(p);
            }
        }

        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            showScanDialog();
        }
    }

    private void showScanDialog() {
        discoveredDevices.clear();

        // 1. Khởi tạo Adapter rỗng
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, android.R.id.text1,
                new ArrayList<String>()) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);

                text1.setPadding(40, 25, 40, 25);
                text1.setTextSize(15);

                String itemText = getItem(position);
                text1.setText(itemText);

                // Chỉ vô hiệu hóa click vào dòng thông báo lỗi, KHÔNG can thiệp đổi màu chữ nữa
                if (itemText != null && itemText.startsWith("❌")) {
                    view.setEnabled(false);
                    view.setOnClickListener(null);
                    text1.setAlpha(0.5f); // Làm mờ đi một chút thay vì đổi màu
                } else {
                    view.setEnabled(true);
                    text1.setAlpha(1.0f);
                }

                return view;
            }
        };

        // 2. Tạo Dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chọn thiết bị kết nối");
        builder.setAdapter(deviceListAdapter, (dialog, which) -> {
            if (discoveredDevices.isEmpty() || which >= discoveredDevices.size())
                return; // Chống crash nếu bấm vào dòng thông báo

            BluetoothDevice selectedDevice = discoveredDevices.get(which);
            if (bikeBleLib != null)
                bikeBleLib.stopScan();
            connectToDevice(selectedDevice.getAddress());
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> {
            if (bikeBleLib != null)
                bikeBleLib.stopScan();
        });

        scanDialog = builder.create();
        scanDialog.show();

        // 3. Tùy chỉnh nút Neutral
        if (bikeBleLib != null) {
            TextView btnNeutral = (TextView) scanDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (btnNeutral != null) {
                btnNeutral.setEnabled(false);
                btnNeutral.setAllCaps(false);
                btnNeutral.setTextSize(11);
                btnNeutral.setTextColor(getColor(R.color.text_sub));
                btnNeutral.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            }
        }

        // 4. CHỜ 100ms ĐỂ LOAD DANH SÁCH THIẾT BỊ ĐÃ LƯU RỒI MỚI QUÉT (NẾU ĐƯỢC PHÉP)
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {

            loadSavedDevicesIntoDialog(); // Đổ dữ liệu Paired devices vào giao diện

            startScanning();
        }, 100);
    }

    // =========================================================
    // HÀM HIỂN THỊ THIẾT BỊ ĐÃ PAIRED (BONDED)
    // =========================================================
    private void loadSavedDevicesIntoDialog() {
        if (bikeBleLib == null)
            return;

        // 1. Quét thiết bị ĐANG KẾT NỐI (Hiển thị 🟢)
        List<BluetoothDevice> connected = bikeBleLib.getConnectedDevices();
        for (BluetoothDevice d : connected) {
            if (!discoveredDevices.contains(d) && d.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                discoveredDevices.add(d);

                // Xử lý triệt để lỗi tên bị rỗng
                String name = d.getName();
                String displayName = (name != null && !name.trim().isEmpty()) ? name
                        : "Xe Đang Kết Nối (" + d.getAddress() + ")";
                deviceListAdapter.add("🟢 " + displayName);
            }
        }

        // 2. Quét thiết bị ĐÃ GHÉP NỐI (Paired/Bonded) NHƯNG CHƯA KẾT NỐI (Hiển thị 🔹)
        Set<BluetoothDevice> bonded = bikeBleLib.getBondedDevices();
        for (BluetoothDevice d : bonded) {
            if (!discoveredDevices.contains(d) && d.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                discoveredDevices.add(d);

                // Xử lý triệt để lỗi tên bị rỗng
                String name = d.getName();
                String displayName = (name != null && !name.trim().isEmpty()) ? name
                        : "Xe Đã Lưu (" + d.getAddress() + ")";
                deviceListAdapter.add("🔹 " + displayName);
            }
        }

        // Báo cho danh sách cập nhật giao diện
        deviceListAdapter.notifyDataSetChanged();
    }

    private void startScanning() {
        if (!bikeBleLib.isBluetoothEnabled()) {
            Toast.makeText(this, "Vui lòng bật Bluetooth!", Toast.LENGTH_SHORT).show();
            return;
        }

        bikeBleLib.startScan(new BikeBleLib.ScanListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, String name, int rssi) {
                if (device != null && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device);
                    String displayName = (name != null ? name : "Thiết bị không tên");

                    runOnUiThread(() -> {
                        deviceListAdapter.add(displayName);
                        deviceListAdapter.notifyDataSetChanged();
                    });
                }
            }
        });
    }

    private void connectToDevice(String macAddress) {
        // LƯU LẠI ĐỊA CHỈ MAC ĐỂ LẦN SAU MỞ APP TỰ KẾT NỐI
        saveDeviceMac(macAddress);

        if (bikeService != null) {
            bikeService.connectToDevice(macAddress);
            return;
        }

        bikeBleLib.setBinaryListener(new BikeBleLib.BinaryListener() {
            @Override
            public void onBinaryReceived(String uuid8, byte[] bytes) {
                DataParser.parseBinary(uuid8, bytes, globalBikeData);
                runOnUiThread(() -> updateUI(globalBikeData));
            }
        });

        bikeBleLib.connect(macAddress, new BikeBleLib.DataListener() {
            @Override
            public void onDataReceived(Map<String, String> data) {
                if (data.containsKey("raw")) {
                    String rawStr = data.get("raw");
                    // Gửi vào DataParser để "đắp" thêm dữ liệu vào biến global
                    DataParser.parseJson(rawStr, globalBikeData);

                    // Cập nhật UI từ cái kho chung này
                    runOnUiThread(() -> updateUI(globalBikeData));
                }
            }
        });
    }

    private void updateUI(BikeData data) {
        tvBikeName.setText(data.name);
        tvBikeModel.setText("Dòng xe: " + data.frame);
        tvBikeFw.setText("Phiên bản: " + data.fw);

        // Thông số cơ bản
        tvBattery.setText(Math.round(data.soc) + "%");
        tvSpeed.setText(String.valueOf(Math.round(data.speed)));
        tvOdo.setText(String.format("ODO: %.1f Km", data.odo));
        tvCycles.setText(String.format("Chu kỳ sạc: %.1f", data.cycles));
        tvEstRange.setText(String.format("%.1f Km", data.kmLeft));
        tvVoltage.setText(String.format("Điện áp: %.2f V", data.voltage));
        tvCurrent.setText(String.format("%.1f A", data.current));

        tvTempPin1.setText(String.format("P1: %.1f°", data.tempPin1));
        tvTempPin2.setText(String.format("P2: %.1f°", data.tempPin2));
        tvTempPin3.setText(String.format("P3: %.1f°", data.tempPin3));
        tvTempPin4.setText(String.format("P4: %.1f°", data.tempPin4));
        tvTempFet.setText(String.format("FET: %.1f°C", data.tempFet));
        tvTempMotor.setText(String.format("Motor: %.1f°C", data.tempMotor));
        tvTempAcu.setText(String.format("ECU: %.1f°C", data.tempController));
        tvTempBms.setText(String.format("BMS: %.1f°C", data.tempBms));

        // Cập nhật Trạng thái vận hành (Drive Mode / Charging / State)
        if (data.pcbState == BikeData.PCB_STATE_OFF) {
            if (data.current > 0) {
                tvDriveMode.setText("ĐANG SẠC");
            } else {
                tvDriveMode.setText("TẮT");
            }
        } else {
            // Phân loại hiển thị theo pcbState đã định nghĩa trong BikeData
            switch (data.pcbState) {
                case BikeData.PCB_STATE_OFF: // 0
                    tvDriveMode.setText("Tắt");
                    break;
                case BikeData.PCB_STATE_PARK: // 1
                    tvDriveMode.setText("P");
                    break;
                case BikeData.PCB_STATE_MODE_D: // 2
                    tvDriveMode.setText("D");
                    break;
                case BikeData.PCB_STATE_MODE_S: // 3
                    tvDriveMode.setText("S");
                    break;
                case BikeData.PCB_STATE_MODE_ON: // 4
                    tvDriveMode.setText("Bật");
                    break;
                default:
                    tvDriveMode.setText("Bật");
                    break;
            }
        }

        // 1. Xử lý Đèn Pha (Sáng/Tắt)
        if (iconSignal != null) {
            if (data.headlight) {
                iconSignal.setTextColor(getColor(R.color.accent_yellow)); // Màu vàng rực
                iconSignal.setAlpha(1.0f);
            } else {
                iconSignal.setTextColor(getColor(R.color.text_sub)); // Màu xám mờ
                iconSignal.setAlpha(0.3f);
            }
        }

        // 2. Xử lý Xi nhan Trái
        if (data.turnLeft) {
            if (iconLeft.getAnimation() == null) {
                iconLeft.startAnimation(blinkAnimation); // Bắt đầu nháy
            }
            iconLeft.setTextColor(getColor(R.color.accent_green));
        } else {
            iconLeft.clearAnimation(); // Dừng nháy
            iconLeft.setTextColor(getColor(R.color.text_sub));
        }

        // 3. Xử lý Xi nhan Phải
        if (data.turnRight) {
            if (iconRight.getAnimation() == null) {
                iconRight.startAnimation(blinkAnimation);
            }
            iconRight.setTextColor(getColor(R.color.accent_green));
        } else {
            iconRight.clearAnimation();
            iconRight.setTextColor(getColor(R.color.text_sub));
        }

        // 4. Xử lý Đèn Pha (Sáng/Tắt)
        if (iconHeadlight != null) {
            if (data.headlight) {
                iconHeadlight.setTextColor(getColor(R.color.accent_yellow)); // Màu vàng rực
                iconHeadlight.setAlpha(1.0f);
            } else {
                iconHeadlight.setTextColor(getColor(R.color.text_sub)); // Màu xám mờ
                iconHeadlight.setAlpha(0.3f);
            }
        }

        // Cập nhật thông số Cell Pin chi tiết
        tvVmin.setText(String.format("Vmin: %.2f V", data.vMin));
        tvVmax.setText(String.format("Vmax: %.2f V", data.vMax));

        // Hiển thị độ lệch cell
        tvCellDiff.setText(String.format("Lệch: %.2f V", data.cellDiff));

        // Mẹo nhỏ: Đổi màu cảnh báo nếu lệch cell quá cao (> 0.050V)
        if (data.cellDiff > 0.050) {
            tvCellDiff.setTextColor(getColor(R.color.accent_red)); // Cảnh báo đỏ
        } else {
            tvCellDiff.setTextColor(getColor(R.color.accent_green)); // An toàn xanh
        }

        if (data.voltage > 0) {
            btnTechInfo.setVisibility(View.VISIBLE);
        } else {
            btnTechInfo.setVisibility(View.INVISIBLE);
        }

        // Kiểm tra quyền điều khiển từ bikeControl
        if (bikeBleLib.bikeControl != null && bikeBleLib.bikeControl.canControl()) {
            tvBikeName.setTextColor(getColor(R.color.accent_green)); // Tên xe màu xanh nếu đã xác thực
            // Bạn có thể cho phép nhấn các nút điều khiển ở đây
        } else {
            tvBikeName.setTextColor(getColor(R.color.text_main)); // Màu trắng bình thường
        }

        int level = bikeBleLib.getSignalStrength(data.rssi);
        updateSignalIcon(level, data.rssi);

        updateFunctionButtonUI(data);

        // === LOGIC HIỂN THỊ TRẠNG THÁI CHỐNG TRỘM ===
        if (data.isAlarmSounding) {
            // Ưu tiên 1: Đang báo động (Chữ đỏ)
            tvLockStatus.setVisibility(View.VISIBLE);
            tvLockStatus.setText("⚠ BỊ DI CHUYỂN");
            tvLockStatus.setTextColor(getColor(R.color.accent_red));
        } else if (data.isArmed) {
            // Ưu tiên 2: Đã khóa an toàn (Chữ xanh lá)
            tvLockStatus.setVisibility(View.VISIBLE);
            tvLockStatus.setText("🛡 Chống trộm");
            tvLockStatus.setTextColor(getColor(R.color.accent_green));
        } else if (data.isLocked) {
            // Ưu tiên 3: Đã khóa an toàn (Chữ xanh lá)
            tvLockStatus.setVisibility(View.VISIBLE);
            tvLockStatus.setText("🛡 Đang khóa");
        } else {
            // Ưu tiên 4: Các trường hợp khác -> Ẩn đi
            tvLockStatus.setVisibility(View.GONE);
        }
    }

    private void updateSignalIcon(int level, int rssiVal) {
        String[] signals = { "📶", "📶", "📶", "📶", "📶" };

        runOnUiThread(() -> {
            if (level >= 0 && level < signals.length) {
                // Hiển thị icon cột sóng + con số dBm thực tế (VD: "📶 -65")
                iconSignal.setText(signals[level] + " " + rssiVal);

                if (level <= 1)
                    iconSignal.setTextColor(getColor(R.color.accent_red)); // Đỏ nếu yếu
                else
                    iconSignal.setTextColor(getColor(R.color.text_main)); // Trắng bình thường
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        // CHỈ KẾT NỐI (BIND) VỚI SERVICE KHI ĐÃ CẤP ĐỦ QUYỀN
        if (hasAllPermissions()) {
            Intent intent = new Intent(this, BikeBackgroundService.class);
            bindService(intent, connection, Context.BIND_AUTO_CREATE);
        }

        IntentFilter filter = new IntentFilter("com.du.dtc.bike.DATA_UPDATED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(dataUpdateReceiver, filter);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
        unregisterReceiver(dataUpdateReceiver);
    }

    private void checkAndRequestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Không ngắt kết nối khi Activity bị đóng để background service tiếp tục chạy
        // if (bikeBleLib != null)
        // bikeBleLib.disconnect();
    }

    private void saveDeviceMac(String mac) {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_MAC, mac).apply();
    }

    private String getSavedDeviceMac() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return prefs.getString(KEY_MAC, null);
    }

    private void checkPermissionsAndAutoConnect() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        boolean allGranted = true;
        for (String p : permissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            // 👉 NẾU ĐÃ CÓ QUYỀN -> LÚC NÀY MỚI KHỞI ĐỘNG SERVICE
            startBikeServiceSafely();
        } else {
            // Nếu chưa có quyền thì yêu cầu quyền (cho lần mở app đầu tiên)
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                // ĐÃ CẤP QUYỀN THÀNH CÔNG -> KHỞI ĐỘNG SERVICE NGAY
                startBikeServiceSafely();
            } else {
                Toast.makeText(this, "Không có quyền Bluetooth, App không thể hoạt động!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startBikeServiceSafely() {
        try {
            Intent serviceIntent = new Intent(this, BikeBackgroundService.class);
            ContextCompat.startForegroundService(this, serviceIntent);

            // Bổ sung: Bind service luôn tại đây nếu trước đó chưa Bind được
            if (!isBound) {
                bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
            }

            // Nếu đã có MAC cũ, tự động kết nối
            String savedMac = getSavedDeviceMac();
            if (savedMac != null && bikeBleLib != null && bikeBleLib.isBluetoothEnabled()) {
                Toast.makeText(this, "Đang kết nối lại với xe...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Người dùng quay lại App -> Chuyển biến trạng thái
        com.du.dtc.bike.ble.BikeBleFreq.isAppActive = true;
        BleDebugLogger.i("BikeApp", "🚀 Mode: Active (High Frequency)");

        // 👉 KÍCH HOẠT LẤY DATA NGAY LẬP TỨC BỎ QUA THỜI GIAN NGỦ
        if (bikeBleLib != null) {
            bikeBleLib.forceImmediatePoll();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Người dùng thoát App/Chuyển app khác -> Quét chậm 30s để tiết kiệm pin
        com.du.dtc.bike.ble.BikeBleFreq.isAppActive = false;
        BleDebugLogger.i("BikeApp", "💤 Mode: Background (Power Saving)");
    }

    // Hàm tiện ích kiểm tra xem đã đủ quyền chưa
    private boolean hasAllPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN);
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT);
        } else {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        for (String p : permissionsNeeded) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}

LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1, location -> {
    float speed = location.getSpeed() * 3.6f;

    TextView tvGps = findViewById(R.id.tv_gps_speed);
    tvGps.setText("GPS: " + (int)speed);
});

TextView tvConnection = findViewById(R.id.tv_connection);

if(isConnected){
    tvConnection.setText("● Connected");
    tvConnection.setTextColor(Color.GREEN);
} else {
    tvConnection.setText("● Disconnected");
    tvConnection.setTextColor(Color.RED);
}

private void sendCommand(String cmd) {

    if (bikeBleLib == null) return;

    if (bikeBleLib.bikeControl == null) {
        Log.d("REGEN", "bikeControl null");
        return;
    }

    if (!bikeBleLib.bikeControl.canControl()) {
        Toast.makeText(this, "Chưa có quyền điều khiển", Toast.LENGTH_SHORT).show();
        return;
    }

    try {
        // 🔥 gửi lệnh xuống xe
        bikeBleLib.bikeControl.sendCustomCommand(cmd);

        Log.d("REGEN", "Sent: " + cmd);

    } catch (Exception e) {
        e.printStackTrace();
    }
}