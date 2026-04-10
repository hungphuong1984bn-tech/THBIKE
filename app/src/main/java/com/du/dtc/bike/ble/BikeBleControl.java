package com.du.dtc.bike.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import com.du.dtc.bike.DatbikeConstants;
import com.du.dtc.bike.log.BleDebugLogger;
import android.content.Intent;

public class BikeBleControl {
    private static final String TAG = "BikeBleControl";

    static {
        System.loadLibrary("bike_secrets");
    }

    // ========================================================================
    // 1. KHAI BÁO NATIVE METHODS
    // ========================================================================
    private static native String getInfoServiceUuid();

    private static native String getBeepActiveCharUuid();

    private static native String getBeepScanCharUuid();

    private static native String getCurrentTimeCharUuid();

    // ========================================================================
    // 2. KHỞI TẠO CÁC HẰNG SỐ UUID
    // ========================================================================
    public static final UUID INFO_SERVICE_UUID = UUID.fromString(getInfoServiceUuid());
    public static final UUID CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    public static final UUID BEEP_ACTIVE_CHAR_UUID = UUID.fromString(getBeepActiveCharUuid());
    public static final UUID BEEP_SCAN_CHAR_UUID = UUID.fromString(getBeepScanCharUuid());
    public static final UUID CURRENT_TIME_CHAR_UUID = UUID.fromString(getCurrentTimeCharUuid());

    // ========================================================================
    // 3. QUẢN LÝ TRẠNG THÁI VÀ ĐIỀU KHIỂN
    // ========================================================================
    private BluetoothGatt bluetoothGatt;

    public BikeBleControl(BluetoothGatt gatt) {
        this.bluetoothGatt = gatt;
    }

    public void updateGatt(BluetoothGatt gatt) {
        this.bluetoothGatt = gatt;
    }

    public boolean canControl() {
        return true;
    }

    // --- CẤU HÌNH LẮNG NGHE (NOTIFY) ---
    public void enableNotification(UUID serviceUuid, UUID charUuid) {
        if (bluetoothGatt == null)
            return;
        BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
            if (characteristic != null) {
                bluetoothGatt.setCharacteristicNotification(characteristic, true);
                BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCCD_UUID);
                if (descriptor != null) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    bluetoothGatt.writeDescriptor(descriptor);
                }
            }
        }
    }

    public void findBike() {
        writeCommandString(INFO_SERVICE_UUID, BEEP_ACTIVE_CHAR_UUID, "1");
    }

    // --- LỆNH KHÔNG YÊU CẦU XÁC THỰC ---
    public void syncCurrentTime() {
        if (bluetoothGatt == null)
            return;
        long currentUnixSeconds = System.currentTimeMillis() / 1000L;

        byte[] timePayload = new byte[4];
        timePayload[0] = (byte) (currentUnixSeconds & 0xFF);
        timePayload[1] = (byte) ((currentUnixSeconds >> 8) & 0xFF);
        timePayload[2] = (byte) ((currentUnixSeconds >> 16) & 0xFF);
        timePayload[3] = (byte) ((currentUnixSeconds >> 24) & 0xFF);

        writeCommandBytes(INFO_SERVICE_UUID, CURRENT_TIME_CHAR_UUID, timePayload);
        BleDebugLogger.i(TAG, "Đã gửi đồng bộ giờ: " + currentUnixSeconds);
    }

    // --- HÀM TIỆN ÍCH GHI & XỬ LÝ DỮ LIỆU ---
    private void writeCommandString(UUID serviceUuid, UUID charUuid, String payload) {
        writeCommandBytes(serviceUuid, charUuid, payload.getBytes(StandardCharsets.UTF_8));
    }

    private void writeCommandBytes(UUID serviceUuid, UUID charUuid, byte[] payload) {
        if (bluetoothGatt == null)
            return;
        BluetoothGattService service = bluetoothGatt.getService(serviceUuid);
        if (service == null)
            return;
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUuid);
        if (characteristic == null)
            return;

        characteristic.setValue(payload);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

}