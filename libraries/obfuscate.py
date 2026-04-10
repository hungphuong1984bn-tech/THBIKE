import os

# 1. Nhóm cho BikeBleControl
SECRETS = {
   "getInfoServiceUuid": "d4905f67-8931-4faa-8c61-86ec7490f3c5",
   "getBeepActiveCharUuid": "350d9a82-b3f3-4213-bdda-6403be495f53",
   "getBeepScanCharUuid": "87efab0d-9e32-4ab8-8916-ed7cb72d819a",
   "getCurrentTimeCharUuid": "44e2bde6-9cd5-4159-9d43-a3e3f4e9c737",
}

# 2. Nhóm cho BikeBleLib
LIB_SECRETS = {
    "getDashboardServiceUuid": "cf6900a7-4520-4a4c-977c-82979e8baccf",
    "getBatteryLogCharUuid": "84c7be0b-7619-45c1-a503-95f4ff8736ed",    
    "getBikeLogCharUuid": "018e6a6f-4bda-7b07-8586-1298248a8d5c",
    "getDashboardCharacteristicUuid": "6d2eb205-6f9b-4ecf-bb1b-a5fad127c66c",
    "getInfoServiceUuid": "d4905f67-8931-4faa-8c61-86ec7490f3c5",
    "getErrorCharacteristicUuid": "460a5bc5-a5f5-4968-b548-dc218935245e",
    "getAuthServiceUuid": "10cb0217-ff02-4474-a1ef-6fd88b5bdaea",
}

# 3. Thông tin mã hóa Key của Log
LOG_SECRET_KEY = "^9T35bN567cvr^6#m"
KEY = 0x5A

c_header = """// AUTO-GENERATED FILE - DO NOT EDIT
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#define SECRET_KEY 0x5A

void xor_cipher(char* data, int len) {
    for (int i = 0; i < len; i++) data[i] ^= SECRET_KEY;
}

jstring get_decrypted_string(JNIEnv* env, const unsigned char* encrypted, int len) {
    char* decrypted = (char*)malloc(len + 1);
    memcpy(decrypted, encrypted, len);
    decrypted[len] = '\\0';
    xor_cipher(decrypted, len);
    jstring result = (*env)->NewStringUTF(env, decrypted);
    free(decrypted);
    return result;
}
"""

final_c_code = c_header

# --- TẠO HÀM UUID CHO BIKEBLECONTROL ---
for name, plain in SECRETS.items():
    encoded = [ord(c) ^ KEY for c in plain]
    hex_array = ", ".join([hex(b) for b in encoded])
    final_c_code += f"""
JNIEXPORT jstring JNICALL Java_com_du_dtc_bike_ble_BikeBleControl_{name}(JNIEnv* env, jclass clazz) {{
    const unsigned char data[] = {{{hex_array}}};
    return get_decrypted_string(env, data, {len(plain)});
}}
"""

# --- TẠO HÀM UUID CHO BIKEBLELIB ---
for name, plain in LIB_SECRETS.items():
    encoded = [ord(c) ^ KEY for c in plain]
    hex_array = ", ".join([hex(b) for b in encoded])
    final_c_code += f"""
JNIEXPORT jstring JNICALL Java_com_du_dtc_bike_ble_BikeBleLib_{name}(JNIEnv* env, jclass clazz) {{
    const unsigned char data[] = {{{hex_array}}};
    return get_decrypted_string(env, data, {len(plain)});
}}
"""

# --- TẠO HÀM MÃ HÓA LOG VỚI KEY ĐÃ BỊ LÀM RỐI ---
# Mã hóa cái chìa khóa LOG_SECRET_KEY
encoded_log_key = [ord(c) ^ KEY for c in LOG_SECRET_KEY]
hex_log_key_array = ", ".join([hex(b) for b in encoded_log_key])

final_c_code += f"""
// ====================================================================
// HÀM MÃ HÓA LOG BẢO MẬT (KEY ẨN)
// ====================================================================
JNIEXPORT jbyteArray JNICALL Java_com_du_dtc_bike_log_BleDebugLogger_encryptLogData(JNIEnv* env, jclass clazz, jbyteArray data) {{
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte* buffer = (*env)->GetByteArrayElements(env, data, NULL);

    // Chìa khóa gốc đã bị làm rối (XOR)
    const unsigned char enc_key[] = {{{hex_log_key_array}}};
    int keyLen = sizeof(enc_key);
    char real_key[keyLen];

    // Giải mã Key ngay trên RAM (Không để lộ string trong file .so)
    for(int i = 0; i < keyLen; i++) {{
        real_key[i] = enc_key[i] ^ SECRET_KEY;
    }}

    // Dùng Key thật để mã hóa Log data
    for(int i = 0; i < len; i++) {{
        buffer[i] ^= real_key[i % keyLen];
    }}

    jbyteArray result = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, result, 0, len, buffer);
    (*env)->ReleaseByteArrayElements(env, data, buffer, JNI_ABORT);
    
    return result;
}}
"""

os.makedirs("app/src/main/cpp", exist_ok=True)
with open("app/src/main/cpp/bike_secrets.c", "w") as f:
    f.write(final_c_code)

print("✅ Đã cập nhật bike_secrets.c: Bao gồm UUID và hàm mã hóa Log với Key ẩn!")