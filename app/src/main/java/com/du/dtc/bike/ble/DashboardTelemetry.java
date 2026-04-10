package com.du.dtc.bike.ble;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DashboardTelemetry {
    public float odo;
    public float speed;
    public float current;

    public boolean isLeftTurn;
    public boolean isRightTurn;
    public boolean isParking;

    public float voltage;
    public int batteryPercent;

    public float batteryTemp;
    public float motorTemp;
    public float controllerTemp;

    public int state;
    public int errorCode;

    public float estimatedRange;

    // Constructor rỗng
    public DashboardTelemetry() {
    }

    /**
     * Hàm lõi: Giải mã 41 byte từ Bluetooth thành các thông số xe
     */
    public static DashboardTelemetry fromBytes(byte[] data) {
        if (data == null || data.length < 40) { // Ít nhất 40 byte
            return null;
        }

        DashboardTelemetry t = new DashboardTelemetry();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Thông số di chuyển & Dòng điện
        t.odo = buffer.getFloat(); // offset 0
        t.speed = buffer.getFloat(); // offset 4
        t.current = buffer.getFloat(); // offset 8

        // 2. Trạng thái đèn báo (Boolean)
        t.isLeftTurn = buffer.get() > 0; // offset 12
        t.isRightTurn = buffer.get() > 0; // offset 13
        t.isParking = buffer.get() > 0; // offset 14

        // 3. Thông số Pin (Điện áp & Phần trăm)
        t.voltage = buffer.getFloat(); // offset 15
        t.batteryPercent = buffer.get() & 0xFF; // offset 19 (Unsigned)

        // 4. Nhiệt độ
        t.batteryTemp = buffer.getFloat(); // offset 20
        t.motorTemp = buffer.getFloat(); // offset 24
        t.controllerTemp = buffer.getFloat();// offset 28

        // 5. Trạng thái và Lỗi
        t.state = buffer.get() & 0xFF; // offset 32
        t.errorCode = buffer.get() & 0xFF; // offset 33

        // 6. Quãng đường dự kiến
        // Vì offset 34, 35, 36 có thể là padding hoặc reserved, ta nhảy thẳng đến 37
        if (data.length >= 41) {
            buffer.position(37);
            t.estimatedRange = buffer.getFloat();
        } else {
            // Xe đời cũ không có biến này, tính nhẩm 1% = 2km
            t.estimatedRange = t.batteryPercent * 2.0f;
        }

        return t;
    }
}