# FILE MANAGER PRO (Android Native)

> **Advanced Android File Manager & Technical Data Explorer**  
> *Một trình quản lý tệp chuyên sâu, phân tích dữ liệu nhị phân, kiểm tra APK toàn diện và khám phá cấu trúc dữ liệu trò chơi Android mà không cần Root.*

---

## 🌟 TÍNH NĂNG NỔI BẬT

1. **Quản lý Tệp Cơ Bản Mạnh Mẽ:**
   - Hỗ trợ đầy đủ: Duyệt thư mục, Tạo mới, Đổi tên, Sao chép, Cắt, Dán, Xóa, Nén ZIP, Giải nén.
   - Chọn nhiều tệp (Multi-select), Đánh dấu sao yêu thích, Tệp gần đây.
   - Tìm kiếm nhanh, sắp xếp đa tiêu chí (Tên, Dung lượng, Ngày sửa đổi, Loại tệp).
   - Chế độ xem Danh sách (List) và Lưới biểu tượng (Grid).
   - Tương thích hoàn toàn với **Storage Access Framework (SAF)**, truy cập an toàn bộ nhớ trong, thẻ nhớ SD ngoài.

2. **APK Inspector – Phân Tích APK Toàn Diện:**
   - Hiển thị tên app, Package name, Version name, Version code, Min SDK, Target SDK.
   - Trích xuất Icon ứng dụng gốc độ phân giải cao.
   - Nhận diện kiến trúc CPU hỗ trợ: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` từ thư mục `lib/`.
   - Phân tích chữ ký số: Chứng chỉ X.509, SHA-256 Fingerprint, nhà phát hành, thời hạn.
   - Danh sách quyền hạn Android (`uses-permission`).
   - Danh sách thành phần: Activities, Services, Broadcast Receivers, Content Providers.
   - Duyệt cây tệp bên trong APK: Các tệp `classes.dex`, thư viện native `.so`, `assets/`, `res/`.

3. **Database Viewer – Trình Xem SQLite Chuyên Sâu:**
   - Mở trực tiếp các tệp `.db`, `.sqlite`, `.sqlite3`.
   - Danh sách bảng kèm số lượng bản ghi chính xác.
   - Xem cấu trúc cột (Tên, kiểu dữ liệu, khóa chính PRIMARY KEY, ràng buộc NOT NULL).
   - Xem câu lệnh SQL `CREATE TABLE` gốc.
   - Lưới dữ liệu phân trang 50 dòng/trang (không làm tràn RAM với cơ sở dữ liệu hàng trăm MB).
   - Tìm kiếm từ khóa theo thời gian thực trên tất cả các cột.
   - **SQL Console:** Chạy các câu lệnh truy vấn `SELECT` tùy chỉnh trong chế độ chỉ đọc an toàn.
   - **Xuất dữ liệu:** Xuất bảng ra định dạng CSV hoặc JSON chỉ với một thao tác.

4. **Binary Viewer & Hex Editor:**
   - Mở mọi định dạng nhị phân: `.bin`, `.dat`, `.blob`, `.raw`, `.dex`, `.obb`, v.v.
   - Giao diện 3 cột chuẩn: Offset (Hex) | Hexadecimal Bytes (16 bytes/dòng) | ASCII Characters.
   - Tải theo khối (Streaming Pages) xử lý tệp hàng GB cực mượt, không tốn RAM.
   - Tìm kiếm chuỗi ASCII hoặc chuỗi byte HEX.
   - Nhảy nhanh đến Offset bất kỳ (hỗ trợ nhập cả Hex `0x...` hoặc số thập phân).
   - Phân tích tự động Magic Header (PNG, JPEG, GIF, WEBP, ZIP, 7Z, RAR, GZIP, SQLite, DEX, ELF, UnityFS, Unreal PAK).
   - Chỉnh sửa byte nhị phân an toàn với kiểm tra xác thực giá trị hex.

5. **Game Data Explorer – Khám Phá Dữ Liệu Trò Chơi:**
   - Nhận diện chuyên sâu các engine game: Unity (`UnityFS`, `UnityWeb`), Unreal Engine (`PAK`), Godot, Dalvik bytecode (`DEX`), Native libs (`libil2cpp.so`, `libmain.so`), SQLite save state, OBB expansion.
   - **Đo lường độ hỗn loạn Shannon Entropy (0 - 8 bits/byte):** Tự động phát hiện dữ liệu game đã bị mã hóa hoặc nén thuật toán riêng khi Entropy > 7.4.
   - Tính toán nhanh Checksum: MD5, SHA-1, SHA-256.
   - **Công cụ so sánh 2 tệp game:** Kiểm tra kích thước và mã băm SHA-256 xem 2 file có giống hệt nhau không.
   - **Tìm tệp trùng lặp (Duplicate Finder):** Quét phát hiện các file trùng lặp nội dung giúp dọn dẹp dung lượng game thừa.

6. **Archive Manager:**
   - Hỗ trợ duyệt nội dung tệp nén: ZIP, APK, JAR, OBB.
   - Trích xuất toàn bộ (với bảo vệ chống lỗ hổng Zip-Slip) hoặc trích xuất từng tệp con.
   - Tạo tệp nén ZIP từ nhiều tệp/thư mục được chọn.
   - Kiểm tra tính toàn vẹn của tệp nén.

7. **Trình Xem & Trình Soạn Thảo Văn Bản / Code:**
   - Hỗ trợ: TXT, JSON, XML, HTML, CSS, JS, PY, JAVA, KT, INI, CFG, LOG, CSV, MD.
   - Đánh số dòng (Line Numbers), tìm kiếm văn bản trong file.
   - Chuyển đổi linh hoạt bảng mã: UTF-8, UTF-16, ISO-8859-1, US-ASCII.
   - Chế độ chỉnh sửa trực tiếp và lưu tệp an toàn.

8. **Trình Xem Đa Phương Tiện:**
   - Xem ảnh với tính năng phóng to / thu nhỏ (Pinch to Zoom) và di chuyển mượt mà.
   - Trình nghe thử nhạc / âm thanh (Audio Preview) với thanh trượt phát và hiển thị thời lượng.
   - Mở nhanh tệp video hoặc tài liệu bằng ứng dụng hệ thống qua FileProvider.

9. **Giao Diện Dark Mode Than Chì M3:**
   - Tông màu nền đen than chì (`#0F1117`), điểm nhấn xanh điện tử (`#2979FF`) và lục bảo (`#00E676`).
   - Tối ưu thao tác một tay trên màn hình điện thoại.

---

## 🛠️ HƯỚNG DẪN TẢI MÃ NGUỒN LÊN GITHUB BẰNG ĐIỆN THOẠI ANDROID

Bạn có thể đưa toàn bộ mã nguồn này lên GitHub trực tiếp từ điện thoại theo một trong các cách sau:

### Cách 1: Sử dụng Ứng Dụng Termux (Khuyên Dùng)
1. Cài đặt **Termux** từ F-Droid.
2. Mở Termux và cài đặt Git:
   ```bash
   pkg update && pkg install git
   ```
3. Di chuyển đến thư mục chứa mã nguồn dự án:
   ```bash
   cd /sdcard/Download/FileManagerPro
   ```
4. Khởi tạo kho lưu trữ Git và tải lên GitHub:
   ```bash
   git init
   git branch -M main
   git add .
   git commit -m "Initial commit - File Manager Pro"
   git remote add origin https://github.com/TÊN_TÀI_KHOẢN_CỦA_BẠN/FileManagerPro.git
   git push -u origin main
   ```

### Cách 2: Sử dụng Web Trình Duyệt trên Điện Thoại
1. Tải toàn bộ mã nguồn về điện thoại dưới dạng tệp `.zip`.
2. Truy cập [github.com](https://github.com) trên trình duyệt (Chrome/Brave), chuyển sang chế độ **"Trang web cho máy tính" (Desktop site)**.
3. Nhấn **New Repository**, đặt tên kho: `FileManagerPro` (Public).
4. Nhấn **Uploading an existing file**, chọn các tệp từ máy hoặc dùng ứng dụng quản lý file để tải lên, sau đó nhấn **Commit changes**.

---

## 🚀 HƯỚNG DẪN TỰ ĐỘNG BIÊN DỊCH APK BẰNG GITHUB ACTIONS

Dự án đã tích hợp sẵn tệp cấu hình `.github/workflows/android.yml`.

1. Sau khi đưa mã nguồn lên GitHub, truy cập vào trang repository của bạn trên GitHub.
2. Nhấn vào tab **Actions** ở menu trên cùng.
3. Bạn sẽ thấy quy trình **"Build File Manager Pro APK"**:
   - Nếu bạn vừa `push` mã lên nhánh `main`, workflow sẽ **tự động bắt đầu chạy**.
   - Nếu muốn chạy thủ công: Nhấn vào workflow bên cột trái -> Nhấn nút **Run workflow** -> Chọn nhánh `main` -> Nhấn **Run workflow**.
4. GitHub Actions sẽ tự động chuẩn bị JDK 17, thiết lập Android SDK, biên dịch mã nguồn Kotlin Jetpack Compose và xuất ra file APK Debug.
5. Quá trình biên dịch thường mất khoảng 2 - 4 phút.

---

## 📥 HƯỚNG DẪN TẢI VÀ CÀI ĐẶT APK

### 1. Tải APK từ GitHub Actions
1. Khi workflow chạy xong (có biểu tượng dấu tích xanh `✓`), nhấn vào phiên chạy đó.
2. Kéo xuống phần **Artifacts** ở cuối trang.
3. Nhấn vào **FileManagerPro-Debug-APK** để tải tệp nén chứa APK về máy.
4. Giải nén tệp vừa tải về, bạn sẽ nhận được tệp `app-debug.apk`.

### 2. Cài Đặt APK Trên Điện Thoại Android
1. Mở tệp `app-debug.apk` vừa tải về.
2. Nếu hệ thống hiển thị cảnh báo *"Cài đặt ứng dụng không rõ nguồn gốc"*, hãy vào Cài đặt -> Cho phép cài đặt từ nguồn này.
3. Nhấn **Cài đặt (Install)** và chờ vài giây.
4. Mở ứng dụng **File Manager Pro** từ màn hình chính và bắt đầu trải nghiệm!

---

## 🔒 AN TOÀN & QUYỀN RIÊNG TƯ
- **100% Hoạt động Offline:** Ứng dụng không yêu cầu kết nối Internet và không gửi bất kỳ dữ liệu nào của bạn ra máy chủ.
- **Không Cần ROOT:** Hoạt động ổn định trên mọi thiết bị Android API 26 trở lên (Android 8.0 đến Android 15/16).
- **Tuân thủ chính sách Google Play:** Sử dụng Storage Access Framework (SAF) và MediaStore an toàn.
