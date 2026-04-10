#!/bin/bash

# Đảm bảo script dừng lại ngay lập tức nếu có bất kỳ lệnh nào bị lỗi
set -e

echo "--------------------------------------------------------"
echo "🚀 Bắt đầu quá trình Build Release cho DTC Bike..."
echo "--------------------------------------------------------"

# 1. Cấp quyền thực thi cho bộ chạy Gradle (nếu chưa có)
chmod +x gradlew

# 2. Dọn dẹp các bản build cũ để tránh xung đột dữ liệu
echo "🧹 Bước 1: Đang dọn dẹp dự án (Clean)..."
./gradlew clean

# 3. Build file App Bundle (.aab) cho bản Release
# Lệnh này sẽ tự động chạy CMake để build code C++ trong src/main/cpp
echo "📦 Bước 2: Đang đóng gói Release App Bundle (.aab)..."
./gradlew bundleRelease

echo "--------------------------------------------------------"
echo "✅ CHÚC MỪNG! BUILD THÀNH CÔNG."
echo "📍 File của bạn đã sẵn sàng tại:"
echo "👉 $(pwd)/app/build/outputs/bundle/release/app-release.aab"
echo "--------------------------------------------------------"
echo "💡 Lưu ý: Hãy dùng file .aab này để upload lên Play Console."