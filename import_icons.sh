#!/bin/bash

# --- CẤU HÌNH ĐƯỜNG DẪN VÀ KÍCH THƯỚC ---
SRC_DIR="icon"
DEST_DIR="app/src/main/res/drawable"
TARGET_SIZE="128x128" # Kích thước chuẩn sắc nét cho Android

# Kiểm tra công cụ convert (thuộc ImageMagick)
if ! command -v convert &> /dev/null
then
    echo "❌ Lỗi: Cần cài đặt ImageMagick để resize ảnh."
    echo "Hãy chạy lệnh: sudo apt-get install imagemagick"
    exit 1
fi

# Tạo thư mục đích nếu chưa tồn tại
mkdir -p "$DEST_DIR"

# Danh sách 4 icon cần xử lý
ICONS=("unlock.png")

echo "🚀 Bắt đầu import và resize icons..."

# Vòng lặp xử lý từng file
for icon in "${ICONS[@]}"; do
    if [ -f "$SRC_DIR/$icon" ]; then
        echo "⏳ Đang xử lý: $icon..."
        # Lệnh convert: Đọc file gốc -> Resize -> Lưu vào thư mục Android
        convert "$SRC_DIR/$icon" -resize $TARGET_SIZE "$DEST_DIR/$icon"
        echo "✅ Đã lưu thành công: $DEST_DIR/$icon"
    else
        echo "⚠️ CẢNH BÁO: Không tìm thấy file '$icon' trong thư mục '$SRC_DIR'!"
    fi
done

echo "🎉 Hoàn tất quá trình copy và resize!"