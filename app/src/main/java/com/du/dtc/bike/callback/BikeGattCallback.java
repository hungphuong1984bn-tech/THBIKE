package com.du.dtc.bike.callback;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;

import com.du.dtc.bike.ble.BikeBleLib;
import com.du.dtc.bike.log.BleDebugLogger;
import com.du.dtc.bike.DatbikeConstants;

public class BikeGattCallback extends BluetoothGattCallback {

    private static final String TAG = "BikeGattCallback";
    private final BikeBleLib bleLib;

    public BikeGattCallback(BikeBleLib bleLib) {
        this.bleLib = bleLib;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            BleDebugLogger.i(TAG, "🟢 Bước 1: Đã kết nối. Đang yêu cầu nới rộng MTU...");

            boolean isMtuRequested = gatt.requestMtu(DatbikeConstants.ANDROID_MTU);

            if (!isMtuRequested) {
                gatt.discoverServices();
            }
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            bleLib.handleDisconnectLogic();
        }
    }

    // Thêm hàm này vào trong class BikeGattCallback
    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BleDebugLogger.i(TAG, "🟢 Bước 2: MTU đã mở rộng thành " + mtu + " bytes. Bắt đầu quét dịch vụ...");
        } else {
            BleDebugLogger.w(TAG, "⚠️ Yêu cầu MTU thất bại. Dùng mức mặc định.");
        }

        // MTU ĐÃ XONG -> LÚC NÀY MỚI ĐƯỢC QUÉT DỊCH VỤ
        gatt.discoverServices();
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BleDebugLogger.i(TAG, "🟢 Bước 3: Đã khám phá xong dịch vụ.");
            bleLib.handleServicesDiscoveredLogic(gatt);
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            bleLib.handleCharacteristicReadLogic(characteristic);
        } else {
            BleDebugLogger.w(TAG, "❌ Đọc thất bại char " + characteristic.getUuid() + " status=" + status);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        bleLib.handleCharacteristicChangedLogic(characteristic);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            BleDebugLogger.d(TAG, "📤 GỬI thành công lệnh tới: " + characteristic.getUuid());
        }
    }

    // BỔ SUNG: Bắt sự kiện đọc RSSI (Keep-Alive)
    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            bleLib.handleRssiRead(rssi);
        }
    }
}