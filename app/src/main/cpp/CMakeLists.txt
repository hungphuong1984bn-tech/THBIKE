cmake_minimum_required(VERSION 3.10.2)
project("bike_secrets")

# Khai báo file C vừa được Python tự tạo ra
add_library(bike_secrets SHARED bike_secrets.c)

# Liên kết với thư viện hệ thống Android
target_link_libraries(bike_secrets log)