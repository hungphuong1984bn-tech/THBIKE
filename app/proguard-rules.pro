# BẢO VỆ CÁC CLASS DATA MODEL (Để Parse JSON không bị lỗi)
# (Bạn cần thay đổi đường dẫn package cho đúng với class BikeData của bạn)
-keep class com.du.dtc.bike.ble.BikeData { *; }

# BẢO VỆ CÁC INTERFACE CỦA BLUETOOTH (Tránh Android hệ thống không gọi được callback)
-keep interface com.du.dtc.bike.ble.BikeBleLib$* { *; }

# Nếu bạn có dùng thư viện bên thứ 3 (như Retrofit, Gson), bạn phải thêm rules của họ vào đây.
# Ví dụ cho thư viện chuẩn của Android:
-keepattributes *Annotation*
-keepattributes Signature