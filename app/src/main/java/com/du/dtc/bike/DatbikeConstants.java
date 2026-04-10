package com.du.dtc.bike;

import java.util.UUID;

public class DatbikeConstants {

    // ==========================================
    // 1. CÁC THÔNG SỐ CẤU HÌNH KẾT NỐI (TIMEOUT & RETRY)
    // ==========================================
    public static final int ANDROID_MTU = 517; // MTU rất lớn, giải thích tại sao gửi OTA nhanh
    public static final int DASHBOARD_REFRESH_INTERVAL = 700; // 💡 ĐỒNG HỒ CẬP NHẬT 700ms/lần

    public static final int MAX_CONNECT_ATTEMPTS = 5;
    public static final int AUTH_RETRY_ATTEMPTS = 5;
    public static final int AUTH_RETRY_DELAY_MS = 1000;
    public static final int SCAN_TIMEOUT = 3000;
    public static final int PAIR_TIMEOUT = 15000;

    // Cấu hình OTA
    public static final int CHUNK_SIZE = 244;
    public static final int VESC_CHUNK_SIZE = 504;
    public static final int TRANSFER_BATCH_SIZE_BLUEDROID = 3;
    public static final int TRANSFER_BATCH_SIZE_NIMBLE = 50;

    public static final String authPIN = "123456";

}