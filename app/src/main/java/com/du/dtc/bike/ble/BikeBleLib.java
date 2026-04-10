package com.du.dtc.bike.ble;

import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.du.dtc.bike.callback.BikeGattCallback;
import com.du.dtc.bike.log.BleDebugLogger;
import com.du.dtc.bike.DatbikeConstants;
import com.du.dtc.bike.BuildConfig;

import java.util.*;

public class BikeBleLib {
    private static final String TAG = "BikeBleLib";

    static {
        System.loadLibrary("bike_secrets");
    }

    // Native methods
    private static native String getDashboardServiceUuid();

    private static native String getBatteryLogCharUuid();

    private static native String getBikeLogCharUuid();

    private static native String getDashboardCharacteristicUuid();

    private static native String getInfoServiceUuid();

    private static native String getAuthServiceUuid();

    private static native String getErrorCharacteristicUuid();

    private final String DASHBOARD_SVC = getDashboardServiceUuid();
    private final String AUTH_SVC = getAuthServiceUuid();
    private final String INFO_SVC = getInfoServiceUuid();

    private final String CHAR_DASHBOARD = getDashboardCharacteristicUuid();
    private final String CHAR_BATTERY_LOG = getBatteryLogCharUuid();
    private final String CHAR_BIKE_LOG = getBikeLogCharUuid();
    private final String CHAR_ERROR = getErrorCharacteristicUuid();

    private int lastRssi = -100;

    // Handler cho Keep-Alive
    private Handler keepAliveHandler = new Handler(Looper.getMainLooper());
    private Runnable keepAliveRunnable;

    // BỔ SUNG: Handler cho Data Polling (Đọc Log định kỳ)
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

    // Handler cho Timeout quét Radar (tránh scan vô thời hạn khi xe xa)
    private Handler scanTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable scanTimeoutRunnable;

    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BluetoothGatt bluetoothGatt;
    private ScanCallback scanCallback;

    private DataListener dataListener;
    private BinaryListener binaryListener;

    public BikeBleControl bikeControl;

    private String lastDeviceAddress;
    private DataListener lastDataListener;
    private boolean isAutoReconnectEnabled = true;
    private int reconnectAttempts = 0;
    private final int MAX_RECONNECT_ATTEMPTS = 5;

    private ScanCallback autoReconnectCallback;
    private final BikeGattCallback gattCallback;
    private boolean isNeedPrintInitLog = true;

    public BikeBleLib(Context context) {
        this.context = context;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        if (manager != null) {
            this.bluetoothAdapter = manager.getAdapter();
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        this.gattCallback = new BikeGattCallback(this);
    }

    // ========================================================================
    // BỔ SUNG: Hàm đọc đặc tính (Characteristic) theo chuỗi UUID
    // ========================================================================
    private void readSpecificChar(String serviceUuidStr, String charUuidStr) {
        if (bluetoothGatt == null)
            return;
        try {
            BluetoothGattService service = bluetoothGatt.getService(UUID.fromString(serviceUuidStr));
            if (service != null) {
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(charUuidStr));
                if (characteristic != null) {
                    bluetoothGatt.readCharacteristic(characteristic);
                }
            }
        } catch (Exception e) {
            BleDebugLogger.e(TAG, "Lỗi đọc UUID: " + e.getMessage());
        }
    }

    // ========================================================================
    // POLLING CHO LOGS & LỖI
    // ========================================================================
    public void startDataPolling(BluetoothGatt gatt) {
        stopDataPolling(); // Dừng vòng lặp cũ nếu đang chạy

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (bluetoothGatt != null) {

                    // 1. Đọc RSSI (Keep-alive) tại thời điểm 0ms
                    bluetoothGatt.readRemoteRssi();

                    // 2. Đọc Battery Log sau 300ms
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        readSpecificChar(DASHBOARD_SVC, CHAR_BATTERY_LOG);
                    }, 300);

                    // 3. Đọc Bike Log sau 600ms
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        readSpecificChar(DASHBOARD_SVC, CHAR_BIKE_LOG);
                    }, 600);

                    // 4. Đọc Error sau 900ms
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        readSpecificChar(INFO_SVC, CHAR_ERROR);
                    }, 900);

                    BleDebugLogger.i(TAG, "Đọc dữ liệu với tần suất " + BikeBleFreq.getPollingInterval());
                    // Lên lịch cho lần chạy TIẾP THEO dựa vào trạng thái App (Active/Background)
                    pollHandler.postDelayed(this, BikeBleFreq.getPollingInterval());
                }
            }
        };

        // YÊU CẦU 1: Chạy ngay lập tức (post) thay vì chờ 1 giây (postDelayed)
        // Để ngay khi xác thực xong là lấy data lên màn hình liền
        pollHandler.post(pollRunnable);
    }

    // YÊU CẦU 2: Hàm ép vòng lặp chạy ngay lập tức, bỏ qua khoảng thời gian đang
    // chờ dở dang
    public void forceImmediatePoll() {
        if (pollRunnable != null && bluetoothGatt != null) {
            // Hủy cái đồng hồ đếm ngược cũ (đang chờ 60s hoặc 1 tiếng)
            pollHandler.removeCallbacks(pollRunnable);
            // Kích hoạt chạy lại ngay lập tức
            pollHandler.post(pollRunnable);
            BleDebugLogger.d(TAG, "⚡ Đã ép lấy dữ liệu ngay lập tức do App vừa Active!");
        }
    }

    public void stopDataPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
        }
    }

    // ========================================================================
    // DELEGATE METHODS
    // ========================================================================
    public void handleDisconnectLogic() {
        BleDebugLogger.w(TAG, "🔴 Đã ngắt kết nối (Xe đi quá xa hoặc tắt máy).");
        stopDataPolling();

        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        if (isAutoReconnectEnabled && lastDeviceAddress != null) {
            BleDebugLogger.d(TAG,
                    "Chuyển sang chế độ Radar ngầm chờ xe xuất hiện. Tần suất " + BikeBleFreq.getScanRadarDelay());
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startTargetedAutoConnect(lastDeviceAddress, lastDataListener);
            }, BikeBleFreq.getScanRadarDelay());
        }
    }

    public void handleServicesDiscoveredLogic(BluetoothGatt gatt) {
        bikeControl = new BikeBleControl(gatt);

        // 1. Đồng bộ giờ
        bikeControl.syncCurrentTime();

        // 2. Kích hoạt Notifications
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            bikeControl.enableNotification(UUID.fromString(DASHBOARD_SVC), UUID.fromString(CHAR_DASHBOARD));
        }, 100);
        // 3. Phục hồi session & Bắt đầu xác thực
        // Phục hồi PIN & Bắt đầu xác thực (Thay thế đoạn code cũ của bạn)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Bắt đầu vòng lặp đọc Log (Vẫn giữ nguyên)
            startDataPolling(gatt);
        }, 600);
    }

    public void handleCharacteristicReadLogic(BluetoothGattCharacteristic characteristic) {
        BleDebugLogger.log(characteristic);
        processIncomingData(characteristic);
    }

    public void handleCharacteristicChangedLogic(BluetoothGattCharacteristic characteristic) {
        BleDebugLogger.log(characteristic);
        processIncomingData(characteristic);
    }

    public void handleRssiRead(int rssi) {
        this.lastRssi = rssi;

        // Phát tín hiệu RSSI về Background Service bằng một UUID giả lập
        if (binaryListener != null) {
            byte[] rssiBytes = new byte[] { (byte) rssi };
            binaryListener.onBinaryReceived("rssi_val", rssiBytes);
        }
    }

    private void processIncomingData(BluetoothGattCharacteristic characteristic) {
        byte[] value = characteristic.getValue();
        if (value != null && value.length > 0) {
            String uuid = characteristic.getUuid().toString().substring(0, 8).toLowerCase();

            // CHỈ đẩy các gói dữ liệu thuần Binary xuống binaryListener
            // Auth UUIDs (c8eaf27b, c75ebe03) đi qua processAuthResponse riêng, không xuống
            // đây
            if (uuid.equals("6d2eb205") || // Dashboard Data (41 bytes)
                    uuid.equals("eec8fd7f") || // Smartkey Echo
                    uuid.equals(CHAR_ERROR.substring(0, 8).toLowerCase())) { // Mã lỗi hệ thống

                if (binaryListener != null) {
                    binaryListener.onBinaryReceived(uuid, value);
                }
            } else {
                // Các UUID còn lại (Bao gồm BATTERY_LOG, BIKE_LOG, Tên xe, Số khung...)
                // Chuyển thành String UTF-8 và gửi về DataListener để Parse JSON
                String textData = new String(value, java.nio.charset.StandardCharsets.UTF_8).trim();
                sendToUI(textData);
            }
        }
    }

    // --- KIỂM TRA TRẠNG THÁI & TÍN HIỆU ---
    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public int getSignalStrength(int rssi) {
        if (rssi > -50)
            return 4;
        else if (rssi > -65)
            return 3;
        else if (rssi > -80)
            return 2;
        else if (rssi > -90)
            return 1;
        else
            return 0;
    }

    public int getLastRssi() {
        return lastRssi;
    }

    // --- QUÉT VÀ KẾT NỐI ---
    public Set<BluetoothDevice> getBondedDevices() {
        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        Set<BluetoothDevice> filtered = new HashSet<>();
        if (bonded != null) {
            for (BluetoothDevice device : bonded) {
                if (isDatBike(device)) {
                    filtered.add(device);
                }
            }
        }
        return filtered;
    }

    public List<BluetoothDevice> getConnectedDevices() {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        List<BluetoothDevice> connected = bluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
        List<BluetoothDevice> filtered = new ArrayList<>();
        if (connected != null) {
            for (BluetoothDevice device : connected) {
                if (isDatBike(device)) {
                    filtered.add(device);
                }
            }
        }
        return filtered;
    }

    public void startScan(final ScanListener listener) {
        if (bluetoothLeScanner == null || !isBluetoothEnabled())
            return;

        // BẢO ĐẢM: Ngừng Radar trước khi quét thủ công để tránh xung đột
        stopTargetedAutoConnect();

        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                // ĐÃ SỬA: TRUYỀN TOÀN BỘ result VÀO THAY VÌ CHỈ TRUYỀN device
                if (isDatBike(result)) {
                    BluetoothDevice device = result.getDevice();
                    String name = device.getName() != null ? device.getName() : "[" + device.getAddress() + "]";
                    listener.onDeviceFound(device, name, result.getRssi());
                }
            }
        };

        bluetoothLeScanner.startScan(null, settings, scanCallback);
        
        // Timeout 15s: Ngừng và tự phục hồi Radar nếu cần
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (scanCallback != null) {
                stopScan();
                resumeRadarIfNeeded();
            }
        }, 15000);
    }

    public void stopScan() {
        if (bluetoothLeScanner != null && scanCallback != null) {
            bluetoothLeScanner.stopScan(scanCallback);
            scanCallback = null;
        }
    }

    private void resumeRadarIfNeeded() {
        if (isAutoReconnectEnabled && !isConnected() && lastDeviceAddress != null) {
            BleDebugLogger.d(TAG, "🔄 Tự phục hồi Radar sau khi quét thủ công...");
            startTargetedAutoConnect(lastDeviceAddress, lastDataListener);
        }
    }

    public void connect(String address, DataListener listener) {
        this.lastDeviceAddress = address;
        this.lastDataListener = listener;
        this.isAutoReconnectEnabled = true;
        this.dataListener = listener;

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                } else {
                    bluetoothGatt = device.connectGatt(context, false, gattCallback);
                }
            } catch (SecurityException e) {
                BleDebugLogger.e(TAG, "Lỗi quyền kết nối: " + e.getMessage());
            }
        }, 300);
    }

    public void disconnect() {
        isAutoReconnectEnabled = false;
        stopTargetedAutoConnect();
        stopDataPolling();

        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }

    public void startTargetedAutoConnect(String macAddress, DataListener listener) {
        this.lastDeviceAddress = macAddress;
        this.lastDataListener = listener;
        this.isAutoReconnectEnabled = true;

        if (!isBluetoothEnabled() || bluetoothLeScanner == null)
            return;

        // BẢO ĐẢM: Dừng quét thủ công nếu đang chạy
        stopScan();
        stopTargetedAutoConnect();
        
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        ScanFilter filter = new ScanFilter.Builder().setDeviceAddress(macAddress).build();
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();

        autoReconnectCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BleDebugLogger.d(TAG, "Đã nhận được kết quả quét: " + result.getDevice().getAddress());
                if (result.getDevice().getAddress().equals(macAddress)) {
                    stopTargetedAutoConnect();
                    connect(macAddress, listener);
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                BleDebugLogger.e(TAG, "Lỗi quét: " + errorCode);
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                BleDebugLogger.d(TAG, "Đã nhận được " + results.size() + " kết quả quét");
            }

        };

        // BẮT ĐẦU QUÉT
        BleDebugLogger.d(TAG, "Radar thử kết nối: " + macAddress);
        bluetoothLeScanner.startScan(Collections.singletonList(filter), settings, autoReconnectCallback);

        final int timeOut = Math.max(BikeBleFreq.getScanRadarDelay(), 10000);

        // ⏱️ TIMEOUT: Nếu hết getScanRadarDelay() mà không thấy xe → dừng & thử lại chu
        // kỳ sau
        scanTimeoutRunnable = () -> {
            if (autoReconnectCallback != null && isAutoReconnectEnabled) {
                BleDebugLogger.d(TAG, "⏱️ Hết thời gian quét (" + timeOut
                        + "ms), không thấy xe. Lên lịch thử lại...");
                stopTargetedAutoConnect();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    startTargetedAutoConnect(macAddress, listener);
                }, BikeBleFreq.getScanRadarDelay());
            }
        };
        scanTimeoutHandler.postDelayed(scanTimeoutRunnable, timeOut);
    }

    public void stopTargetedAutoConnect() {
        // Hủy timeout nếu đang chờ
        if (scanTimeoutRunnable != null) {
            scanTimeoutHandler.removeCallbacks(scanTimeoutRunnable);
            scanTimeoutRunnable = null;
        }
        if (bluetoothLeScanner != null && autoReconnectCallback != null) {
            bluetoothLeScanner.stopScan(autoReconnectCallback);
            autoReconnectCallback = null;
        }
    }

    // Lọc thiết bị quét được (có kèm Gói quảng cáo ScanRecord)
    public boolean isDatBike(ScanResult result) {
        if (result == null || result.getDevice() == null) {
            BleDebugLogger.d(TAG, "Không phải xe Datbike (case 5)");
            return false;
        }

        BluetoothDevice device = result.getDevice();

        // 1. Dùng chung lưới lọc cơ bản (Tên, MAC đã lưu, Class)
        if (isDatBike(device)) {
            return true;
        }

        // 2. ✨ THE MAGIC: Kiểm tra UUID trong Gói Quảng Cáo bằng HashSet<ParcelUuid>
        android.bluetooth.le.ScanRecord scanRecord = result.getScanRecord();
        if (scanRecord != null && scanRecord.getServiceUuids() != null) {
            Set<android.os.ParcelUuid> targetUuids = new HashSet<>(Arrays.asList(
                    android.os.ParcelUuid.fromString(DASHBOARD_SVC),
                    android.os.ParcelUuid.fromString(AUTH_SVC),
                    android.os.ParcelUuid.fromString(INFO_SVC)));
            for (android.os.ParcelUuid uuid : scanRecord.getServiceUuids()) {
                if (targetUuids.contains(uuid)) {
                    BleDebugLogger.d(TAG,
                            "🔍 Bắt được xe Datbike qua UUID từ ScanRecord: " + uuid + " | " + device.getAddress());
                    return true;
                }
            }
        }

        // 3. FALLBACK: Kiểm tra theo tên từ ScanRecord (nếu tên Device gốc bị null)
        if (device.getName() == null && scanRecord != null && scanRecord.getDeviceName() != null) {
            String name = scanRecord.getDeviceName();
            String lowerName = name.toLowerCase();
            if (lowerName.contains("dat bike") || lowerName.contains("datbike") ||
                    lowerName.contains("quantum") || lowerName.contains("weaver")) {
                return true;
            }
        }

        BleDebugLogger.d(TAG, "Không phải xe Datbike (case 1): " + device.getAddress() + " | " + device.getName());

        return false;
    }

    // Lọc thiết bị bộ nhớ đệm (Connected/Bonded) - Không có Gói quảng cáo
    public boolean isDatBike(BluetoothDevice device) {
        if (device == null)
            return false;

        // 1. LỌC RÁC CƠ BẢN (Tai nghe, chuột, máy tính, điện thoại...)
        if (device.getType() == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
            BleDebugLogger.d(TAG,
                    "Không phải xe Datbike (case 2): " + device.getAddress() + " | " + device.getName() + " | "
                            + device.getType());
            return false;
        }

        if (device.getBluetoothClass() != null) {
            int majorClass = device.getBluetoothClass().getMajorDeviceClass();
            if (majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    majorClass == BluetoothClass.Device.Major.COMPUTER ||
                    majorClass == BluetoothClass.Device.Major.PHONE ||
                    majorClass == BluetoothClass.Device.Major.WEARABLE) {
                BleDebugLogger.d(TAG,
                        "Không phải xe Datbike (case 3): " + device.getAddress() + " | " + device.getName() + " | "
                                + majorClass);
                return false;
            }
        }

        // 2. FAST PASS (Trùng MAC xe đang dùng)
        android.content.SharedPreferences prefs = context.getSharedPreferences("BikeAppPrefs", Context.MODE_PRIVATE);
        String savedMac = prefs.getString("saved_mac_address", "");
        if (!savedMac.isEmpty() && device.getAddress().equalsIgnoreCase(savedMac)) {
            return true;
        }

        // 4. KIỂM TRA THEO TÊN NẾU CÓ
        String name = device.getName();
        if (name != null) {
            String lowerName = name.toLowerCase();
            if (lowerName.contains("dat bike") || lowerName.contains("datbike") ||
                    lowerName.contains("quantum") || lowerName.contains("weaver")) {
                return true;
            }
        }

        // 5. CACHED UUIDs (Tuyệt chiêu cuối với xe đã Bonded sẵn từ trước)
        // Hệ điều hành Android có thể lén cache lại quảng cáo UUID của xe dù hiện tại
        // không nằm trong vùng quét
        if (device.getUuids() != null) {
            Set<android.os.ParcelUuid> targetUuids = new HashSet<>(Arrays.asList(
                    android.os.ParcelUuid.fromString(DASHBOARD_SVC),
                    android.os.ParcelUuid.fromString(AUTH_SVC),
                    android.os.ParcelUuid.fromString(INFO_SVC)));
            for (android.os.ParcelUuid uuid : device.getUuids()) {
                if (targetUuids.contains(uuid)) {
                    return true;
                }
            }
        }

        BleDebugLogger.d(TAG, "Không phải xe Datbike (case 4): " + device.getAddress() + " | " + device.getName());

        return false;
    }

    // --- INTERFACES & HELPERS ---
    public interface ScanListener {
        void onDeviceFound(BluetoothDevice device, String name, int rssi);
    }

    public interface DataListener {
        void onDataReceived(Map<String, String> data);
    }

    public interface BinaryListener {
        void onBinaryReceived(String uuid8, byte[] bytes);
    }

    public void setBinaryListener(BinaryListener listener) {
        this.binaryListener = listener;
    }

    private void sendToUI(String rawStr) {
        if (dataListener != null) {
            Map<String, String> dataMap = new HashMap<>();
            dataMap.put("raw", rawStr);
            dataListener.onDataReceived(dataMap);
        }
    }

    // Trả về true nếu điện thoại đang duy trì kết nối sóng BLE thành công với xe
    public boolean isConnected() {
        return this.bluetoothGatt != null;
    }
}