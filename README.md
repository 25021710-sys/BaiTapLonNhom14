#  Hệ Thống Đấu Giá Trực Tuyến — Nhóm 14

Ứng dụng đấu giá trực tuyến theo mô hình **Client–Server** (Java Socket + JavaFX), hỗ trợ nhiều người dùng đồng thời, cập nhật giá thầu real-time, và quản trị phiên đấu giá bởi Admin.

---

##  Công Nghệ & Môi Trường

- **Java 21**, JavaFX 21.0.2, Maven 3.8+
- **MySQL** (TiDB Cloud), HikariCP, Gson, BCrypt
- **Hệ điều hành:** Windows / macOS / Linux (yêu cầu màn hình đồ họa cho Client)

---

## Cấu Trúc Module Chính

```
src/main/java/com/auction/
├── client/        # Giao diện JavaFX + kết nối Socket
├── server/        # Socket Server, Service, DAO, Observer
└── common/        # DTO, Request, Response dùng chung
```

---

## Chạy Chương Trình

>  **Khởi động Server trước, sau đó mới chạy Client.**

### 1. Build

```bash
mvn clean package -DskipTests
```

### 2. Chạy Server

```bash
# Windows / macOS / Linux
mvn exec:java "-Dexec.mainClass=com.auction.server.network.SocketServer"
```

Server lắng nghe mặc định trên cổng **8080**. Để đổi cổng:
```bash
mvn exec:java -Dexec.mainClass="com.auction.server.network.SocketServer" -Dexec.args="9090"
```

### 3. Chạy Client

Mở terminal mới (giữ Server đang chạy):

```bash
# Windows / macOS / Linux
mvn javafx:run
```

> **Linux:** Nếu thiếu thư viện GTK: `sudo apt-get install libgtk-3-0 libgl1-mesa-glx`

Có thể chạy nhiều Client song song để test đa người dùng.

### 4. Kết nối qua mạng LAN

Sửa `DEFAULT_HOST` trong `SocketClient.java` thành IP của máy Server:
```java
private static final String DEFAULT_HOST = "192.168.x.x";
```
Sau đó build lại trên máy Client.

---

## Chức Năng Đã Hoàn Thành

**Người dùng:** Đăng ký / Đăng nhập, nạp tiền, tạo yêu cầu đấu giá, tham gia phòng đấu giá, đặt giá thầu real-time, xem lịch sử bid.

**Admin:** Duyệt / từ chối yêu cầu tạo phiên, quản lý phòng (tạm dừng, tiếp tục, đóng, hủy + hoàn tiền), xem dashboard real-time.

**Hệ thống:** Anti-sniping (gia hạn phiên khi bid sát giờ kết thúc), tự động kết thúc phiên, giao dịch tài chính an toàn (lock theo auctionId), broadcast real-time, Observer Pattern, Unit Test (JUnit 5 + Mockito).

---

## Tài Liệu & Demo

| Nội dung | Liên kết |
|---|---|
| 📄 Báo cáo PDF | *https://drive.google.com/file/d/1T3RvcW8Ce3CATzpWXdqXrNoMll2xV8Gs/view?usp=drive_link* |
| 🎬 Video demo | *https://drive.google.com/file/d/1LxUNeMPNUnHL9Rr6Wn0GwAO38u1GjC-b/view?usp=drive_link* |