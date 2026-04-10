package com.du.dtc.bike.log;

import android.bluetooth.BluetoothGattCharacteristic;
import android.util.Log;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

import com.du.dtc.bike.BuildConfig;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Thư viện hỗ trợ Debug dữ liệu BLE và ghi Log hệ thống.
 * Tự động vô hiệu hóa toàn bộ Log khi Build Release.
 */
public class BleDebugLogger {

    public static final boolean DEBUG = BuildConfig.MY_DEBUG_FLAG;
    private static final String TAG = "BikeBleLib";
    private static final int MAX_HISTORY_SIZE = 100; // Tăng lên 15 để lưu trữ được nhiều hơn khi export

    // Cache giờ sẽ lưu Object LogRecord thay vì chỉ chuỗi MD5
    private static final HashMap<String, LinkedList<LogRecord>> md5HistoryCache = new HashMap<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());
    static {
        System.loadLibrary("bike_secrets");
    }

    // 1. KHAI BÁO HÀM NATIVE
    public static native byte[] encryptLogData(byte[] data);

    public static void i(String tag, String msg) {
        if (DEBUG)
            Log.i("MainActivity", "[" + tag + "] " + msg);
    }

    public static void d(String tag, String msg) {
        if (DEBUG)
            Log.d("MainActivity", "[" + tag + "] " + msg);
    }

    public static void e(String tag, String msg) {
        if (DEBUG)
            Log.e("MainActivity", "[" + tag + "] " + msg);
    }

    public static void w(String tag, String msg) {
        if (DEBUG)
            Log.w("MainActivity", "[" + tag + "] " + msg);
    }

    public static void v(String tag, String msg) {
        if (DEBUG)
            Log.v("MainActivity", "[" + tag + "] " + msg);
    }

    // --- HÀM LOG DỮ LIỆU BLE ĐẶC THÙ ---

    // Thêm hàm logData chung
    private static void logData(String key, String md5, String hexData, String stringData, boolean checkDuplicate) {
        if (!DEBUG)
            return;

        synchronized (md5HistoryCache) {
            LinkedList<LogRecord> history = md5HistoryCache.get(key);
            if (history == null) {
                history = new LinkedList<>();
                md5HistoryCache.put(key, history);
            }

            if (checkDuplicate) {
                boolean isDuplicate = false;
                for (LogRecord record : history) {
                    if (record.md5.equals(md5)) {
                        isDuplicate = true;
                        break;
                    }
                }
                if (isDuplicate)
                    return;
            }

            // Thêm dữ liệu mới vào lịch sử
            history.addLast(new LogRecord(md5, hexData, stringData));
            if (history.size() > MAX_HISTORY_SIZE * 2) { // Tăng nhẹ size để lưu text log thoải mái hơn
                history.removeFirst();
            }
        }
    }

    /**
     * Ghi một đoạn text bất kỳ vào cache Log bảo mật và văng ra Logcat.
     */
    public static void logText(String category, String text) {
        if (!DEBUG)
            return;
        String currentMd5 = calculateMD5(text.getBytes(StandardCharsets.UTF_8));
        String key = (category != null && !category.isEmpty()) ? category : "SYSTEM";
        logData(key, currentMd5, "", text, false);
        i(TAG, "📝 [" + key + "] " + text);
    }

    public static void log(BluetoothGattCharacteristic characteristic) {
        if (!DEBUG)
            return;

        if (characteristic == null || characteristic.getValue() == null)
            return;

        byte[] data = characteristic.getValue();
        if (data.length == 0)
            return;

        String charUuid = characteristic.getUuid().toString().substring(0, 8);
        String currentMd5 = calculateMD5(data);

        String stringData = new String(data).replaceAll("[\\x00-\\x1F]", "").trim();
        String hexData = bytesToHex(data);

        // Lưu vào cache
        logData(charUuid, currentMd5, hexData, stringData, true);

        // Vẫn in ra Logcat bình thường
        i(TAG, "┌──────────────────────────────────────────────────────────");
        i(TAG, "│ 📡 [" + charUuid + "]");

        boolean isJson = stringData.contains("{") && stringData.contains("}");
        boolean isShortCommand = stringData.length() <= 10 && stringData.matches("^[a-zA-Z0-9_]+$");

        if (isJson || isShortCommand) {
            i(TAG, "│ String  : " + stringData);
        } else {
            i(TAG, "│ Hex     : " + hexData);
        }
        i(TAG, "└──────────────────────────────────────────────────────────");
    }

    public static void clearCache() {
        if (!DEBUG)
            return;
        synchronized (md5HistoryCache) {
            md5HistoryCache.clear();
        }
    }

    private static String calculateMD5(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(data);
            byte[] messageDigest = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String h = Integer.toHexString(0xFF & b);
                while (h.length() < 2)
                    h = "0" + h;
                hexString.append(h);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "ERROR";
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    // 2. HÀM XUẤT VÀ CHIA SẺ LOG BẢO MẬT
    public static void exportAndShareLogs(Context context) {
        StringBuilder logOutput = new StringBuilder();

        // 2.1 Thu thập thông tin thiết bị
        logOutput.append("=== THÔNG TIN THIẾT BỊ ===\n");
        logOutput.append("Hãng: ").append(Build.MANUFACTURER).append("\n");
        logOutput.append("Model: ").append(Build.MODEL).append("\n");
        logOutput.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        logOutput.append("App Package: ").append(context.getPackageName()).append("\n");
        logOutput.append("==========================\n\n");

        // 2.2 Bổ sung xuất lịch sử Characteristic Cache
        logOutput.append("=== LỊCH SỬ DỮ LIỆU ĐẶC TÍNH BẮT ĐƯỢC (CACHED RAW DATA) ===\n");
        synchronized (md5HistoryCache) {
            if (md5HistoryCache.isEmpty()) {
                logOutput.append("Chưa có dữ liệu nào được ghi nhận.\n");
            } else {
                for (String uuid : md5HistoryCache.keySet()) {
                    logOutput.append("UUID: [").append(uuid).append("]\n");
                    LinkedList<LogRecord> records = md5HistoryCache.get(uuid);
                    if (records != null) {
                        for (LogRecord record : records) {
                            String timeStr = dateFormat.format(new Date(record.timestamp));
                            logOutput.append("  ↳ [").append(timeStr).append("]\n");

                            // Nếu chuỗi chứa JSON hoặc ký tự in được, xuất String. Nếu không, chỉ xuất Hex
                            boolean isJson = record.stringData.contains("{") && record.stringData.contains("}");
                            if (isJson || record.stringData.matches("^[a-zA-Z0-9_]+$")) {
                                logOutput.append("      String: ").append(record.stringData).append("\n");
                            }
                            logOutput.append("      Hex:    ").append(record.hexData).append("\n");
                        }
                    }
                    logOutput.append("\n");
                }
            }
        }
        logOutput.append("===========================================================\n\n");

        // 2.3 Thu thập Logcat hệ thống
        logOutput.append("=== SYSTEM LOGCAT ===\n");
        try {
            Process process = Runtime.getRuntime().exec(
                    "logcat -d MainActivity:D *:S");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                logOutput.append(line).append("\n");
            }

            // Chuyển toàn bộ text thành mảng bytes
            byte[] rawBytes = logOutput.toString().getBytes(StandardCharsets.UTF_8);

            // 👉 MÃ HÓA BẢO MẬT BẰNG C (NDK)
            byte[] encryptedBytes = encryptLogData(rawBytes);

            // // 2.4 Copy vào Clipboard (Phải chuyển sang Base64 để text không bị lỗi mã
            // hóa)
            // String base64Log = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
            // android.content.ClipboardManager clipboard =
            // (android.content.ClipboardManager) context
            // .getSystemService(Context.CLIPBOARD_SERVICE);
            // android.content.ClipData clip =
            // android.content.ClipData.newPlainText("BikeAppLogs", base64Log);
            // clipboard.setPrimaryClip(clip);
            // Toast.makeText(context, "Đã copy Log (Đã mã hóa) vào bộ nhớ tạm!",
            // Toast.LENGTH_SHORT).show();

            // 2.5 Tạo file Binary và chia sẻ cho App khác
            shareLogAsFile(context, encryptedBytes);

        } catch (Exception e) {
            Toast.makeText(context, "Lỗi khi lấy Log: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static void shareLogAsFile(Context context, byte[] encryptedData) {
        try {
            File cachePath = new File(context.getCacheDir(), "logs");
            cachePath.mkdirs();
            File file = new File(cachePath, "BikeApp_Logs.bin");

            FileOutputStream stream = new FileOutputStream(file);
            stream.write(encryptedData);
            stream.close();

            Uri contentUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

            if (contentUri != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.setType("application/octet-stream");

                context.startActivity(Intent.createChooser(shareIntent, "Chia sẻ file Log mật qua..."));
            }

        } catch (Exception e) {
            Toast.makeText(context, "Lỗi chia sẻ file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}