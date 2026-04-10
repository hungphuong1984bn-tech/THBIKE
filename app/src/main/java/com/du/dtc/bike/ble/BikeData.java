package com.du.dtc.bike.ble;

import java.util.ArrayList;
import java.util.List;

public class BikeData {

    public static final int PCB_STATE_OFF = 0; // Xe tắt hẳn
    public static final int PCB_STATE_MODE_ON = 1; // Xe đã bật điện nhưng chưa vào P
    public static final int PCB_STATE_PARK = 2; // Trạng thái P (Park)
    public static final int PCB_STATE_MODE_D = 3; // Chế độ lái D
    public static final int PCB_STATE_MODE_S = 4; // Chế độ lái S

    // --- THÔNG TIN CƠ BẢN ---
    public String name = "N/A"; // Tên xe (Xe của Dương)
    public String frame = "N/A"; // Model (QT_S3)
    public String fw = "N/A"; // Firmware (1.11.0)
    public String fwHash = "N/A"; // Mã định danh firmware duy nhất
    public String vin = "N/A"; // Số khung (frame)

    // --- VẬN HÀNH (MAIN PCB) ---
    public double speed = 0.0; // Tốc độ hiện tại (km/h)
    public double odo = 0.0; // Tổng quãng đường (km)
    public double kmLeft = 0.0; // Dự kiến còn lại (km)
    public double throttle = 0.0; // Vị trí tay ga (0.0 - 1.0)
    public boolean turnLeft = false; // Xi nhan trái
    public boolean turnRight = false; // Xi nhan phải
    public boolean headlight = false; // Đèn pha
    public int pcbState = 0; // Trạng thái vận hành (Park/Drive)
    public int pcbError = 0; // Mã lỗi bo mạch chính
    public boolean idleOff = false; // Tự động tắt máy (true = Bật, false = Tắt)

    // --- HỆ THỐNG PIN (BMS) ---
    public double voltage = 0.0; // Tổng điện áp pin (V)
    public double current = 0.0; // Dòng điện tức thời (A) - Dương là xả, Âm là sạc
    public double soc = 0.0; // Phần trăm pin (%)
    public boolean isCharging = false;// Trạng thái sạc
    public boolean isDischarging = false; // Trạng thái xả
    public double tempBalanceReg = 0.0; // Nhiệt độ điện trở cân bằng
    public int bmsError = 0; // Mã lỗi BMS
    public double tempBms;

    // Nhiệt độ
    public double tempFet = 0.0; // Nhiệt độ sò công suất (FET)
    public double tempPin1 = 0.0, tempPin2 = 0.0, tempPin3 = 0.0, tempPin4 = 0.0; // 4 cảm biến NTC

    // Chi tiết Cell
    public List<Double> cellVoltages = new ArrayList<>(); // Danh sách 23 cell
    public double vMin = 0.0; // Điện áp cell thấp nhất
    public double vMax = 0.0; // Điện áp cell cao nhất
    public double cellDiff = 0.0; // Độ lệch điện áp giữa các cell

    // Sức khỏe & Chu kỳ
    public double cycles = 0.0; // Số chu kỳ sạc (Cycles)
    public double currentAh = 0.0; // Dung lượng Ah hiện tại
    public double absAh = 0.0; // Tổng năng lượng đã tiêu thụ từ trước tới nay

    // --- BỘ ĐIỀU KHIỂN (CONTROLLER) ---
    public double tempMotor = 0.0; // Nhiệt độ động cơ
    public double adc1 = 0.0, adc2 = 0.0; // Điện áp cảm biến tay ga

    // Raw binary từ char 6d2eb205 (motor controller telemetry)
    public double tempController = 0.0; // Nhiệt độ bộ điều khiển (offset 20-23) ~34°C khi sạc
    // Ghi chú offset 4-7 = int32 const=7 (có thể là số cực từ motor hoặc gear)
    // Ghi chú offset 12-15 = chưa xác định (liên quan speed=0?)
    // Ghi chú offset 16-19 = float ~0.047 (chưa xác định, duty cycle low?)

    // Raw binary từ char c8eaf27b (service 10cb0217)
    public double rawCtrlFloat = 0.0; // float ~0.124 từ c8eaf27b (có thể duty cycle hoặc tay ga ADC)

    // --- THÔNG TIN TỪ ---
    public boolean isLocked = false; // Trạng thái khóa xe (true = Đã khóa)
    public boolean isAlarmSounding = false; // Xe đang báo động (kêu còi)
    public boolean isArmed = false; // Xe đang ở chế độ bảo vệ chống trộm

    // Tham biến nội bộ của app
    public int rssi = -100; //
}