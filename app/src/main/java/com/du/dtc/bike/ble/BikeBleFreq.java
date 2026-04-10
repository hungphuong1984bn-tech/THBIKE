package com.du.dtc.bike.ble;

import com.du.dtc.bike.activity.MainActivity;

public class BikeBleFreq {

    // Trạng thái App: true = đang xem, false = chạy nền
    public static boolean isAppActive = true;

    public static boolean isAllowHistoryLog = true;

    // Chu kỳ gửi lệnh Keep-Alive (Đọc RSSI) để xe không ngắt kết nối
    public static int getKeepAliveInterval() {
        return 5000; // 5 giây
    }

    // Thời gian trễ trước khi bật Radar tìm xe lại khi mất kết nối
    public static int getScanRadarDelay() {

        if (isAppActive) {
            return 1000;
        }

        return 60 * 1000; // 60 giây khi chạy nền
    }

    public static int getPollingInterval() {

        if (isAppActive) {
            return 1000;
        }

        if (MainActivity.globalBikeData.pcbState == com.du.dtc.bike.ble.BikeData.PCB_STATE_OFF) {
            return 3600000; // 1 tiếng khi xe tắt
        }

        if (MainActivity.globalBikeData.pcbState == com.du.dtc.bike.ble.BikeData.PCB_STATE_MODE_D) {
            return 5000; // 5 giây khi xe chạy
        }

        if (MainActivity.globalBikeData.pcbState == com.du.dtc.bike.ble.BikeData.PCB_STATE_MODE_S) {
            return 5000; // 5 giây khi xe chạy
        }

        return 60000; // 60 giây khi chạy nền
    }

    public static int getLogInterval() {

        if (MainActivity.globalBikeData.pcbState == com.du.dtc.bike.ble.BikeData.PCB_STATE_MODE_D) {
            return 30 * 1000; // 30 giây khi xe chạy
        }

        if (MainActivity.globalBikeData.pcbState == com.du.dtc.bike.ble.BikeData.PCB_STATE_MODE_S) {
            return 30 * 1000; // 30 giây khi xe chạy
        }

        if (MainActivity.globalBikeData.pcbState == com.du.dtc.bike.ble.BikeData.PCB_STATE_PARK) {
            return 5 * 60 * 1000; // 5 phút khi xe tắt
        }

        if (MainActivity.globalBikeData.pcbState == com.du.dtc.bike.ble.BikeData.PCB_STATE_OFF) {
            return 3600000; // 1 tiếng khi xe tắt
        }

        return 60000; // 60 giây khi chạy nền
    }

}