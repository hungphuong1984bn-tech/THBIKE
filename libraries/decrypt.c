#include <stdio.h>
#include <stdlib.h>
#include <string.h>

//gcc decrypt.c -o decrypt

// Hàm giải mã Stream (File hoặc STDIN)
void decrypt_stream(FILE *in, FILE *out) {
    // Chìa khóa phải khớp 100% với file bike_secrets.c trong Android
    const char key[] = "^9T35bN567cvr^6#m";
    int keyLen = strlen(key);
    int i = 0;
    int ch;
    
    // Đọc từng byte, giải mã XOR và in ra màn hình
    while ((ch = fgetc(in)) != EOF) {
        fputc(ch ^ key[i % keyLen], out);
        i++;
    }
}

int main(int argc, char *argv[]) {
    FILE *in = stdin; // Mặc định đọc từ luồng nhập chuẩn

    // Nếu người dùng có truyền file vào
    if (argc == 2) {
        in = fopen(argv[1], "rb");
        if (!in) {
            perror("Lỗi: Không thể mở file");
            return 1;
        }
    } else if (argc > 2) {
        fprintf(stderr, "Cách 1 (Từ File): %s <tên_file.bin>\n", argv[0]);
        fprintf(stderr, "Cách 2 (Từ Clipboard Base64): echo 'chuỗi_base64' | base64 -d | %s\n", argv[0]);
        return 1;
    }

    // Thực thi giải mã ra màn hình (stdout)
    decrypt_stream(in, stdout);

    if (in != stdin) {
        fclose(in);
    }
    return 0;
}