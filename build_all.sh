#!/bin/bash

# 1. Định nghĩa các biến cơ bản
APP_NAME="Datbike"
OUTPUT_DIR="./html/dist"
APK_PATH="./app/build/outputs/apk"

echo "🚀 Bắt đầu quy trình Build & Deploy..."
echo "--------------------------------------"

# 2. Cấp quyền thực thi cho gradlew
chmod +x gradlew

# 3. Dọn dẹp bản build cũ và tạo thư mục đích
echo "🧹 Đang làm sạch project và thư mục dist..."
./gradlew clean
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"

# 4. Thực hiện Build đồng thời 2 phiên bản
echo "🏗️  Đang biên dịch Staging & Release (Minified)..."
./gradlew assembleStaging assembleRelease

# 5. Kiểm tra kết quả build
if [ $? -eq 0 ]; then
    echo "--------------------------------------"
    echo "✅ Build THÀNH CÔNG! Đang copy vào hệ thống..."

    # Copy bản Staging
    if [ -f "${APK_PATH}/staging/app-staging.apk" ]; then
        cp "${APK_PATH}/staging/app-staging.apk" "${OUTPUT_DIR}/${APP_NAME}_Staging.apk"
        echo "📦 Đã xuất: ${OUTPUT_DIR}/${APP_NAME}_Staging.apk"
    fi

    # Copy bản Release
    if [ -f "${APK_PATH}/release/app-release.apk" ]; then
        cp "${APK_PATH}/release/app-release.apk" "${OUTPUT_DIR}/${APP_NAME}_Release.apk"
        echo "📦 Đã xuất: ${OUTPUT_DIR}/${APP_NAME}_Release.apk"
    fi

    echo "--------------------------------------"
    echo "🎉 Hoàn tất! File đã sẵn sàng tại: ${OUTPUT_DIR}"
    ls -lh "${OUTPUT_DIR}"
else
    echo "❌ Lỗi: Build thất bại. Vui lòng kiểm tra lại lỗi code."
    exit 1
fi