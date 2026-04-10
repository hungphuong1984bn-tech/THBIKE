package com.du.dtc.bike.ble;

import org.json.JSONArray;
import org.json.JSONObject;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import com.du.dtc.bike.log.BleDebugLogger;
import com.du.dtc.bike.activity.MainActivity;

public class DataParser {

    /**
     * Parse dữ liệu binary từ các characteristic nhị phân.
     * * @param uuid 8 ký tự đầu của UUID characteristic
     * 
     * @param bytes mảng byte nhận được
     * @param data  đối tượng BikeData (Biến Global) để ghi kết quả vào
     */
    public static void parseBinary(String uuid, byte[] bytes, BikeData data) {
        if (bytes == null || bytes.length == 0)
            return;

        try {
            ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

            switch (uuid) {
                // ========================================================
                // 1. DASHBOARD DATA (Tốc độ, Pin, Odo, Nhiệt độ) - 41 bytes
                // ========================================================
                case "6d2eb205": {
                    DashboardTelemetry telemetry = DashboardTelemetry.fromBytes(bytes);
                    if (telemetry != null) {
                        // Cập nhật vào biến Global
                        data.odo = telemetry.odo;
                        data.speed = telemetry.speed;
                        data.current = telemetry.current;
                        data.voltage = telemetry.voltage;
                        data.soc = telemetry.batteryPercent; // Pin (%)
                        data.kmLeft = telemetry.estimatedRange;

                        data.tempBms = telemetry.batteryTemp;
                        data.tempMotor = telemetry.motorTemp;
                        data.tempController = telemetry.controllerTemp;

                        data.turnLeft = telemetry.isLeftTurn;
                        data.turnRight = telemetry.isRightTurn;

                        triggerStageChange(data.pcbState, telemetry.state);
                        data.pcbState = telemetry.state;
                        data.pcbError = telemetry.errorCode;

                        // BleDebugLogger.d("DataParser", "Dashboard: " + data.speed + " km/h | Pin: " +
                        // data.soc + "%");
                    }
                    break;
                }

                // ========================================================
                // 2. LOCK STATUS (Trạng thái ổ khóa thông minh)
                // ========================================================
                case "eec8fd7f": {
                    if (bytes.length >= 1) {
                        int lockFlag = buf.get(0) & 0xFF; // Byte đầu tiên
                        updateLockStatus(lockFlag, data);
                    }
                    break;
                }

                // c8eaf27b = Auth Challenge UUID, không phải data characteristic
                // → Xử lý riêng trong BikeBleControl.processAuthResponse(), không parse ở đây

                case "c75ebe03": {
                    // Báo hiệu quẹt thẻ NFC
                    if (bytes.length >= 2) {
                        short val = buf.getShort(0);
                        if (val != 0) {
                            BleDebugLogger.i("DataParser", "[c75ebe03] Thẻ NFC quẹt: " + val);
                        }
                    }
                    break;
                }

                default:
                    // BleDebugLogger.d("DataParser", "[" + uuid + "] Binary " + bytes.length + "B
                    // chưa có parser");
            }

        } catch (Exception e) {
            BleDebugLogger.e("DataParser", "parseBinary lỗi [" + uuid + "]: " + e.getMessage());
        }
    }

    public static void parseJson(String jsonStr, BikeData data) {
        if (jsonStr == null || jsonStr.isEmpty())
            return;

        try {
            // BƯỚC 1: Lọc lấy phần JSON chuẩn.
            // Nếu là gói nhị phân (không có dấu { }), hàm sẽ tự thoát và giữ nguyên dữ liệu
            // cũ.
            int start = jsonStr.indexOf("{");
            int end = jsonStr.lastIndexOf("}");
            if (start == -1 || end == -1)
                return;

            String cleanJson = jsonStr.substring(start, end + 1);
            JSONObject json = new JSONObject(cleanJson);

            // BƯỚC 2: Phân tích gói PIN & NHIỆT ĐỘ (UUID 84c7be0b)
            // {"voltage":76.99, "current":3.02, "charge":true, "fet":34.59, "NTCs":[...],
            // "soc":{...}, "cellVols":[...]}
            if (json.has("voltage")) {
                data.voltage = json.optDouble("voltage", data.voltage);
                data.current = json.optDouble("current", data.current);
                data.isCharging = json.optBoolean("charge", data.isCharging);
                data.isDischarging = json.optBoolean("discharge", data.isDischarging);
                data.tempFet = json.optDouble("fet", data.tempFet);
                data.tempBalanceReg = json.optDouble("resistor", data.tempBalanceReg);
                data.bmsError = json.optInt("error", data.bmsError);

                // Sub-object SOC:
                // {"V":3.34,"I":0.76,"Ah":8.43,"cycles":36.82,"soc":0.58,"absAh":1067.8}
                if (json.has("soc")) {
                    JSONObject socObj = json.getJSONObject("soc");
                    data.soc = socObj.optDouble("soc", 0.0) * 100; // 0.58 -> 58%
                    data.cycles = socObj.optDouble("cycles", data.cycles);
                    data.currentAh = socObj.optDouble("Ah", data.currentAh);
                    data.absAh = socObj.optDouble("absAh", data.absAh);
                }

                // THAY THẾ ĐOẠN NTCs CŨ BẰNG ĐOẠN NÀY:
                if (json.has("NTCs")) {
                    JSONArray ntcs = json.getJSONArray("NTCs");
                    int len = ntcs.length();
                    if (len > 0) {
                        double sumTemp = 0;
                        // Cộng tổng nhiệt độ các cảm biến
                        for (int i = 0; i < len; i++) {
                            sumTemp += ntcs.optDouble(i, 0.0);
                        }
                        // Nhiệt độ BMS = Trung bình cộng của các cell NTC (~33.5°C)
                        data.tempBms = sumTemp / len;

                        // Vẫn giữ lại việc gán chi tiết từng pin nếu bạn cần dùng ở đâu đó
                        if (len >= 4) {
                            data.tempPin1 = ntcs.optDouble(0, data.tempPin1);
                            data.tempPin2 = ntcs.optDouble(1, data.tempPin2);
                            data.tempPin3 = ntcs.optDouble(2, data.tempPin3);
                            data.tempPin4 = ntcs.optDouble(3, data.tempPin4);
                        }
                    }
                }
            }

            // BƯỚC 3: Phân tích gói CẤU HÌNH + VẬN HÀNH (UUID 018e6a6f)
            // {"bikeConfig":{...}, "firmware":{...}, "controller":{...}, "mainPcb":{...}}

            // 3a. Thông tin xe (bikeConfig)
            if (json.has("bikeConfig")) {
                JSONObject cfg = json.getJSONObject("bikeConfig");
                data.name = cfg.optString("name", data.name);
                data.frame = cfg.optString("model", data.frame);
                data.vin = cfg.optString("frame", data.vin); // Số khung thực tế (VIN)
                data.idleOff = cfg.optBoolean("idleOff", data.idleOff);
            }

            // 3b. Thông tin firmware
            if (json.has("firmware")) {
                JSONObject fw = json.getJSONObject("firmware");
                data.fw = fw.optString("version", data.fw);
                data.fwHash = fw.optString("hash", data.fwHash);
            }

            // 3c. Bộ điều khiển (controller):
            // {"ecu":0,"motor":0,"adc1":0.85,"adc2":0.85,"phase":0}
            if (json.has("controller")) {
                JSONObject ctrl = json.getJSONObject("controller");
                data.adc1 = ctrl.optDouble("adc1", data.adc1);
                data.adc2 = ctrl.optDouble("adc2", data.adc2);
                // "motor": giá trị 0 hiện tại, cần debug thêm để xác nhận là nhiệt độ hay mã
                // trạng thái
                data.tempMotor = ctrl.optDouble("motor", data.tempMotor);
            }

            // 3d. Bo mạch chính (mainPcb)
            if (json.has("mainPcb")) {
                JSONObject pcb = json.getJSONObject("mainPcb");
                data.odo = pcb.optDouble("odo", data.odo);
                data.speed = pcb.optDouble("speed", data.speed);
                data.kmLeft = pcb.optDouble("kmLeft", data.kmLeft);
                data.throttle = pcb.optDouble("throttle", data.throttle);
                data.pcbError = pcb.optInt("error", data.pcbError);
                data.turnLeft = pcb.optBoolean("left", data.turnLeft);
                data.turnRight = pcb.optBoolean("right", data.turnRight);
                data.headlight = pcb.optBoolean("headlight", data.headlight);

                int newState = pcb.optInt("state", data.pcbState);
                triggerStageChange(data.pcbState, newState);
                data.pcbState = newState;
            }

            // BƯỚC 4: Xử lý mảng Cell (cellVols) từ gói BMS
            if (json.has("cellVols")) {
                JSONArray cells = json.getJSONArray("cellVols");
                data.cellVoltages.clear();

                double min = 5.0;
                double max = 0.0;

                for (int i = 0; i < cells.length(); i++) {
                    double v = cells.optDouble(i, 0.0);
                    data.cellVoltages.add(v);
                    if (v < min && v > 0)
                        min = v;
                    if (v > max)
                        max = v;
                }

                if (cells.length() > 0) {
                    data.vMin = min;
                    data.vMax = max;
                    data.cellDiff = max - min;
                }
            }

        } catch (Exception e) {
            // Không log lỗi ở đây để tránh làm rác Logcat khi gặp gói nhị phân
        }
    }

    /**
     * Giải mã gói dữ liệu Dashboard (41 bytes) từ xe.
     * Sử dụng class DashboardTelemetry làm trung gian giải mã.
     */
    public static void parseDashboard(byte[] bytes, BikeData data) {
        if (bytes == null || bytes.length == 0)
            return;

        try {
            // Sử dụng class DashboardTelemetry mà chúng ta đã tạo trước đó
            DashboardTelemetry telemetry = DashboardTelemetry.fromBytes(bytes);

            if (telemetry != null) {
                // Cập nhật dữ liệu từ telemetry sang object global BikeData
                data.odo = telemetry.odo;
                data.speed = telemetry.speed;
                data.current = telemetry.current;
                data.voltage = telemetry.voltage;
                data.soc = telemetry.batteryPercent; // Phần trăm pin
                data.kmLeft = telemetry.estimatedRange;

                // Cập nhật nhiệt độ
                data.tempBms = telemetry.batteryTemp;
                data.tempMotor = telemetry.motorTemp;
                data.tempController = telemetry.controllerTemp;

                // Cập nhật trạng thái rẽ (Xi nhan)
                data.turnLeft = telemetry.isLeftTurn;
                data.turnRight = telemetry.isRightTurn;

                // Nếu class BikeData của bạn chưa có biến isParking, bạn có thể tạo thêm
                // boolean isParking;
                // data.isParking = telemetry.isParking;

                // Cập nhật mã lỗi và trạng thái bo mạch
                data.pcbError = telemetry.errorCode;

                int newState = telemetry.state;
                triggerStageChange(data.pcbState, newState);
                data.pcbState = newState;
            }
        } catch (Exception e) {
            BleDebugLogger.e("DataParser", "Lỗi parseDashboard: " + e.getMessage());
        }
    }

    /**
     * Giải mã trạng thái Khóa thông minh (Smartkey Echo) từ xe.
     * Trả về các cờ báo hiệu xe đang khóa, mở, hoặc đang réo còi báo động.
     */
    public static void parseLockStatus(byte[] bytes, BikeData data) {
        if (bytes == null || bytes.length == 0)
            return;

        try {
            int lockFlag = bytes[0] & 0xFF;
            updateLockStatus(lockFlag, data);

        } catch (Exception e) {
            BleDebugLogger.e("DataParser", "Lỗi parseLockStatus: " + e.getMessage());
        }
    }

    private static void updateLockStatus(int lockFlag, BikeData data) {
        data.isAlarmSounding = false; // <--- Tắt báo động
        data.isArmed = false; // <--- Tắt báo động

        switch (lockFlag) {
            case 49: // Khóa
                data.isLocked = true;
                BleDebugLogger.d("DataParser", "🔒 Trạng thái: XE ĐÃ KHÓA");
                break;

            case 48: // Mở khóa
                data.isLocked = false;
                BleDebugLogger.d("DataParser", "🔓 Trạng thái: XE ĐÃ MỞ KHÓA");
                break;

            case 2: // Báo động
                BleDebugLogger.w("DataParser", "🚨 CẢNH BÁO: BÁO ĐỘNG ĐANG KÊU!");
                data.isAlarmSounding = true; // <--- Bật báo động
                break;

            case 7: // Đang bảo vệ
                data.isArmed = true;
                BleDebugLogger.d("DataParser", "🛡️ Trạng thái: ĐANG BẢO VỆ CHỐNG TRỘM");
                break;

            default:
                BleDebugLogger.d("DataParser", "Trạng thái khóa không xác định: " + lockFlag);
        }
    }

    private static void triggerStageChange(int oldState, int newState) {
        if (oldState == BikeData.PCB_STATE_OFF && newState != BikeData.PCB_STATE_OFF) {
            MainActivity.bikeBleLib.bikeControl.syncCurrentTime();
        }
    }
}