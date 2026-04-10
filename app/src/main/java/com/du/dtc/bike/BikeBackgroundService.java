package com.du.dtc.bike;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.du.dtc.bike.ble.BikeBleLib;
import com.du.dtc.bike.ble.BikeData;
import com.du.dtc.bike.log.BleDebugLogger;
import android.content.Context;
import com.du.dtc.bike.ble.DataParser;
import com.du.dtc.bike.db.BikeDatabase;
import com.du.dtc.bike.db.BikeLogEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.app.PendingIntent;
import com.du.dtc.bike.ble.BikeBleFreq;

public class BikeBackgroundService extends Service {
    private static final String TAG = "BikeBackgroundService";
    private static final String CHANNEL_ID = "BikeBackgroundChannel";
    private static final int NOTIFICATION_ID = 888;

    private final IBinder binder = new LocalBinder();
    private BikeBleLib bikeBleLib;
    public BikeData globalBikeData = new BikeData();
    private ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private long lastLogTime = 0;

    private MediaPlayer alarmPlayer;
    private AudioManager audioManager;
    private int originalVolume;
    private boolean isUserMuted = false;
    private boolean wasAlarming = false;

    public static final String ACTION_MUTE_ALARM = "com.du.dtc.bike.ACTION_MUTE_ALARM";

    public class LocalBinder extends Binder {
        public BikeBackgroundService getService() {
            return BikeBackgroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        Notification notification = createNotification("DTC Bike: Dịch vụ đang chạy ngầm");

        // BỌC TRY-CATCH NHIỀU LỚP: ƯU TIÊN connectedDevice, FALLBACK VỀ GENERIC FOREGROUND
        boolean foregroundStarted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                foregroundStarted = true;
            } catch (SecurityException e) {
                BleDebugLogger.e(TAG, "Chưa đủ quyền connectedDevice FGS, thử fallback: " + e.getMessage());
            }
        }
        if (!foregroundStarted) {
            // Fallback: chạy foreground service không có type cụ thể
            // Đảm bảo service không bị hệ thống giết trên Android 8+ (API 26+)
            try {
                startForeground(NOTIFICATION_ID, notification);
                foregroundStarted = true;
            } catch (Exception e2) {
                BleDebugLogger.e(TAG, "Không thể khởi động FGS: " + e2.getMessage());
                stopSelf();
            }
        }

        bikeBleLib = new BikeBleLib(this);
        setupBleListeners();

        // Tự động kết nối lại nếu service bị khởi động lại
        String savedMac = getSharedPreferences("BikeAppPrefs", Context.MODE_PRIVATE)
                .getString("saved_mac_address", null);
        if (savedMac != null) {
            connectToDevice(savedMac);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_MUTE_ALARM.equals(intent.getAction())) {
            isUserMuted = true;
            stopPhoneAlarm();
            updateNotification("🚨 Báo động đang kêu (Đã tắt còi ĐT)");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bikeBleLib != null) {
            bikeBleLib.disconnect();
        }
        dbExecutor.shutdown();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    private void setupBleListeners() {
        bikeBleLib.setBinaryListener((uuid8, bytes) -> {
            if (uuid8.equals("6d2eb205")) {
                DataParser.parseDashboard(bytes, globalBikeData);
                processDataUpdate();
            } else if (uuid8.equals("eec8fd7f")) {
                DataParser.parseLockStatus(bytes, globalBikeData);
                processDataUpdate();
            } else if (uuid8.equals("c8eaf27b") || uuid8.equals("c75ebe03")) {
                // Auth Token / Card
            } else if (uuid8.equals("rssi_val")) {
                // HỨNG DỮ LIỆU SÓNG (RSSI) Ở ĐÂY
                globalBikeData.rssi = bytes[0]; // Cập nhật biến Global
                broadcastDataUpdate(); // CHỈ bắn broadcast để UI update, KHÔNG lưu log database
            } else {
                DataParser.parseBinary(uuid8, bytes, globalBikeData);
            }
        });
    }

    public void connectToDevice(String macAddress) {
        updateNotification("Đang kết nối đến " + macAddress);
        // Dùng chế độ Radar ngầm thay vì connect trực tiếp
        bikeBleLib.startTargetedAutoConnect(macAddress, data -> {
            // Callback này dùng để hứng dữ liệu text/chuỗi (như tên xe, số khung)
            if (data.containsKey("raw")) {
                DataParser.parseJson(data.get("raw"), globalBikeData);
                processDataUpdate();
            }
        });
    }

    public void disconnect() {
        if (bikeBleLib != null)
            bikeBleLib.disconnect();
        updateNotification("Disconnected");
    }

    private void processDataUpdate() {
        checkAlarmStatus(); // Kiểm tra báo động mỗi khi có dữ liệu mới
        logDataIfNecessary();
        broadcastDataUpdate();
    }

    private void logDataIfNecessary() {
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= BikeBleFreq.getLogInterval()) {
            lastLogTime = now;
            BikeLogEntity entity = new BikeLogEntity();
            entity.timestamp = now;
            entity.speed = (int) Math.round(globalBikeData.speed);
            entity.odo = globalBikeData.odo;

            // Battery and Power
            entity.voltage = globalBikeData.voltage;
            entity.current = globalBikeData.current;
            entity.soc = globalBikeData.soc;

            // Temperatures
            entity.tempBalanceReg = globalBikeData.tempBalanceReg;
            entity.tempFet = globalBikeData.tempFet;
            entity.tempPin1 = globalBikeData.tempPin1;
            entity.tempPin2 = globalBikeData.tempPin2;
            entity.tempPin3 = globalBikeData.tempPin3;
            entity.tempPin4 = globalBikeData.tempPin4;
            entity.tempMotor = globalBikeData.tempMotor;
            entity.tempController = globalBikeData.tempController;

            // Cells
            if (globalBikeData.cellVoltages != null) {
                entity.cellVoltages = new java.util.ArrayList<>(globalBikeData.cellVoltages);
            }

            dbExecutor.execute(() -> {
                BikeDatabase.getDatabase(this).bikeLogDao().insertLog(entity);
            });
        }
    }

    private void broadcastDataUpdate() {
        Intent intent = new Intent("com.du.dtc.bike.DATA_UPDATED");
        intent.setPackage(getPackageName()); // Ép cờ nội bộ để Android gửi tới receiver đang chặn RECEIVER_NOT_EXPORTED
        sendBroadcast(intent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bike Connection Status",
                    NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    // Cập nhật hàm tạo notification để thêm nút "Tắt còi" khi đang báo động
    private Notification createNotification(String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DTC Bike")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                // Ưu tiên cao để hiện popup khi có báo động
                .setPriority(NotificationCompat.PRIORITY_MAX);

        if (globalBikeData.isAlarmSounding && !isUserMuted) {
            Intent muteIntent = new Intent(this, BikeBackgroundService.class);
            muteIntent.setAction(ACTION_MUTE_ALARM);
            PendingIntent pendingMute = PendingIntent.getService(this, 0, muteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            builder.addAction(android.R.drawable.ic_lock_silent_mode, "TẮT CÒI ĐIỆN THOẠI", pendingMute);
            builder.setFullScreenIntent(null, true);
        }

        return builder.build();
    }

    public void updateNotification(String text) {
        BleDebugLogger.logText(TAG, text);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(text));
        }
    }

    public BikeBleLib getBikeBleLib() {
        return bikeBleLib;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        BleDebugLogger.w(TAG, "⚠️ Ứng dụng vừa bị vuốt tắt khỏi đa nhiệm! Kích hoạt hồi sinh...");

        // Tạo Intent để gọi lại chính Service này
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        // Yêu cầu PendingIntent có cờ FLAG_IMMUTABLE cho Android 12 trở lên
        android.app.PendingIntent restartServicePendingIntent = android.app.PendingIntent.getService(
                getApplicationContext(),
                1,
                restartServiceIntent,
                android.app.PendingIntent.FLAG_ONE_SHOT | android.app.PendingIntent.FLAG_IMMUTABLE);

        // Cài báo thức gọi lại Service sau 1000ms (1 giây)
        android.app.AlarmManager alarmService = (android.app.AlarmManager) getApplicationContext()
                .getSystemService(Context.ALARM_SERVICE);
        if (alarmService != null) {
            alarmService.set(
                    android.app.AlarmManager.ELAPSED_REALTIME,
                    android.os.SystemClock.elapsedRealtime() + 1000,
                    restartServicePendingIntent);
        }
    }

    private void checkAlarmStatus() {
        if (globalBikeData.isAlarmSounding) {
            if (!wasAlarming) {
                isUserMuted = false; // Reset trạng thái mute khi có đợt báo động mới
                wasAlarming = true;
            }

            if (!isUserMuted) {
                startPhoneAlarm();
                updateNotification("🚨 CẢNH BÁO: XE ĐANG BỊ DI CHUYỂN!");
            }
        } else {
            if (wasAlarming) {
                wasAlarming = false;
                isUserMuted = false;
                stopPhoneAlarm();
                updateNotification("Đang kết nối");
            }
        }
    }

    private void startPhoneAlarm() {
        try {
            if (audioManager == null) {
                audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            }
            // Ép âm lượng tối đa
            originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            if (alarmPlayer == null) {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                alarmPlayer = new MediaPlayer();
                alarmPlayer.setDataSource(this, alarmUri);
                alarmPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
                alarmPlayer.setLooping(true);
                alarmPlayer.prepare();
            }
            if (!alarmPlayer.isPlaying())
                alarmPlayer.start();
        } catch (Exception e) {
            BleDebugLogger.e(TAG, "Lỗi còi báo động: " + e.getMessage());
        }
    }

    private void stopPhoneAlarm() {
        if (alarmPlayer != null) {
            alarmPlayer.stop();
            alarmPlayer.release();
            alarmPlayer = null;
            if (audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
            }
        }
    }

}
