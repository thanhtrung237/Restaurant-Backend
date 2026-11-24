# Restaurant-Backend

## Ba container độc lập: FE, BE, MySQL

Chạy trong `Restaurant-Backend` với hai repo BE/FE nằm cạnh nhau:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-docker.ps1 -Build
```

Script build hai image ứng dụng và dùng image MySQL chính thức, sau đó chạy ba
container bằng `docker run`. Docker Desktop hiển thị từng container riêng,
không nằm trong nhóm Compose. Compose chỉ được dùng để đọc cấu hình môi trường.

| Container | Truy cập từ máy tính | Cổng bên trong |
| --- | --- | --- |
| `restaurant-frontend` | http://localhost:8088 | 80 |
| `restaurant-backend` | http://localhost:8081 | 8080 |
| `restaurant-mysql` | `127.0.0.1:3307` | 3306 |

MySQL Workbench dùng host `127.0.0.1`, port `3307`; username/password/database
lấy từ `.env.docker`. Các service dùng mạng `restaurant-network`, alias
`mysql`, `backend`, `frontend`. FE vẫn gọi API/WebSocket qua Nginx cùng origin.
Các port có thể đổi bằng `FRONTEND_PORT`, `BACKEND_PORT`, `MYSQL_PORT`.
`MYSQL_USER` phải là tài khoản ứng dụng, ví dụ `restaurant`, không đặt `root`.
Tài khoản quản trị `root` dùng mật khẩu riêng ở `MYSQL_ROOT_PASSWORD`.

Volume mặc định `restaurant-local_mysql_data` được dùng lại từ bộ Compose cũ.
Nếu bộ Compose đang chạy, dừng nó bằng `docker compose --env-file .env.docker stop`
trước khi chuyển chế độ. Không chạy hai MySQL cùng ghi vào volume này. Script
kiểm tra và dừng nếu volume đang được một container đang chạy khác sử dụng.

Build/chạy lại từng container độc lập sau khi sửa code:

```powershell
.\start-docker.ps1 -Service backend -Build -Recreate
.\start-docker.ps1 -Service frontend -Build -Recreate
.\start-docker.ps1 -Service mysql
```

Nếu PowerShell chặn thực thi `.ps1`, dùng dạng
`powershell -ExecutionPolicy Bypass -File .\start-docker.ps1` và thêm các tham số
`-Service`, `-Build`, `-Recreate` tương ứng ở cuối lệnh.

Không có `-Recreate`, script giữ container đã tồn tại và khởi động nó nếu đang
dừng. `-Recreate` chỉ thay container thuộc script này, không xóa database volume.
Các file env trung gian được tạo trong `.docker-runtime` đã Git ignore và được
xóa sau khi chạy; không in credentials ra terminal.

```powershell
docker logs -f --tail=100 restaurant-backend
docker logs -f --tail=100 restaurant-frontend
docker logs -f --tail=100 restaurant-mysql

# Dừng riêng từng container; database vẫn được giữ trong volume
docker stop restaurant-frontend
docker stop restaurant-backend
docker stop restaurant-mysql

# Khởi động lại cả ba theo thứ tự và đợi healthcheck
.\start-docker.ps1
```

Database mới vẫn rỗng, chưa có tài khoản. Để nhập dump database của project vào
container độc lập, dừng ứng dụng trong lúc nhập:

```powershell
docker stop restaurant-frontend restaurant-backend
docker cp .\restaurant.sql restaurant-mysql:/tmp/restaurant.sql
docker exec restaurant-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/restaurant.sql'
docker exec restaurant-mysql rm /tmp/restaurant.sql
.\start-docker.ps1
```

Chỉ dùng dump đúng database, tránh `USE` trỏ sang tên database khác và lưu ý dump
có `DROP TABLE` sẽ thay bảng hiện tại. Không import vào dữ liệu cần giữ nếu chưa
có backup.

## Swagger / OpenAPI cho backend

- Swagger UI: **http://localhost:8081/swagger-ui/index.html**
- OpenAPI JSON: **http://localhost:8081/v3/api-docs**
- API ứng dụng: `http://localhost:8081/api/v1/...`
- Health: `http://localhost:8081/actuator/health/readiness`

Swagger tự tạo tài liệu từ controller/DTO hiện có, dùng springdoc 2.6.0 tương
thích Spring Boot 3.3.5. Đăng nhập ở operation `/api/v1/auth/login`, lấy access
token rồi nhấn **Authorize** và dán token (không thêm `Bearer` phía trước).
Endpoint đăng nhập/refresh được mô tả public; endpoint được bảo vệ dùng JWT.
Phân quyền theo vai trò/tenant và kiểm tra guest session vẫn được thực thi.
Guest endpoint có thể cần `X-Guest-Token` riêng.

Swagger không lưu token qua lần tải trang tiếp theo. Đặt `SWAGGER_ENABLED=false`
và recreate backend để tắt UI lẫn OpenAPI JSON. Khi chạy trên production cần
quyết định rõ có công khai tài liệu hay không.

## Chạy local bằng Docker Compose (tùy chọn)

Đây là chế độ thay thế script standalone ở trên; không chạy đồng thời cả hai
chế độ trên cùng cổng và volume.

Bộ Compose chạy MySQL 8.4, backend Java 21 và frontend Nginx. Đây là cấu hình
local, dùng database riêng trong Docker volume và `ddl-auto=update` để tạo bảng.
Chưa bao gồm HTTPS, migration production hay GitHub Actions.

Hai Git repo phải nằm cạnh nhau:

```text
KLTN/
├── RestaurantApp-BE/   # compose.yaml nằm tại đây
└── RestaurantApp-FE/
```

Nếu thư mục FE có tên khác, đặt `FRONTEND_BUILD_CONTEXT` trong `.env.docker`.

### 1. Chuẩn bị

- Cài và mở [Docker Desktop cho Windows](https://docs.docker.com/desktop/setup/install/windows-install/),
  dùng WSL 2 và Linux containers. Kiểm tra `docker version` và `docker compose version`.
- Cần Internet cho lần build đầu để tải base image, Gradle và dependency.
- Không cần cài MySQL, Node hay Java trên host để chạy Docker.
- Nếu chưa có `.env.docker`, sao chép file mẫu; không ghi đè file đã có:

```powershell
cd E:\TanTai\KhoaLuanTotNghiep\KLTN_project\Restaurant-Backend
if (-not (Test-Path .env.docker)) {
    Copy-Item .env.docker.example .env.docker
}
```

Mở `.env.docker`, đặt hai mật khẩu MySQL khác nhau và hai JWT secret khác nhau.
Mỗi JWT secret là Base64 của ít nhất 32 byte ngẫu nhiên. Tạo từng secret bằng
PowerShell (chạy hai lần):

```powershell
$keyBytes = New-Object byte[] 64
$keyGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
try { $keyGenerator.GetBytes($keyBytes) } finally { $keyGenerator.Dispose() }
[Convert]::ToBase64String($keyBytes)
```

`.env.docker` đã được Git ignore. Compose chỉ nhận các biến được khai báo trong
`compose.yaml`; không dùng hoặc copy `.env` hiện tại của backend vào image.
Nếu Codex đã tạo `.env.docker` cho bạn, các mật khẩu/JWT local đã được sinh ngẫu
nhiên và bạn có thể dùng ngay. Không đưa chúng lên GitHub.

### 2. Build và chạy

Chạy các lệnh sau trong `Restaurant-Backend`:

```powershell
docker compose --env-file .env.docker config --quiet
docker compose --env-file .env.docker up -d --build --wait --wait-timeout 300
docker compose --env-file .env.docker ps
```

Mở **http://localhost:8088**. Backend được map ra `localhost:8081` để truy cập
Swagger/API, MySQL ra `localhost:3307` cho Workbench. Backend đợi MySQL chạy được truy vấn; frontend đợi
backend readiness kiểm tra kết nối database thành công.

- `/api/v1/...` được Nginx chuyển sang `backend:8080`, giữ nguyên URL và query.
- `/ws` và các URL con SockJS được proxy, hỗ trợ WebSocket Upgrade.
- Refresh các đường dẫn React Router vẫn trả về `index.html`.
- `/healthz` kiểm tra Nginx; nó không thay thế readiness của backend.

Kiểm tra frontend và endpoint SockJS qua proxy:

```powershell
Invoke-WebRequest http://localhost:8088/healthz -UseBasicParsing
Invoke-WebRequest http://localhost:8088/ws/info -UseBasicParsing
docker compose --env-file .env.docker exec backend curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

Nếu port 8088 đang bận, đổi `FRONTEND_PORT` và các origin tương ứng trong
`APP_CORS_ALLOWED_ORIGIN_PATTERNS`, rồi chạy lại `up -d`.

### 3. Dữ liệu và tài khoản đăng nhập

Lần đầu MySQL tạo database rỗng. Hibernate tạo schema, nhưng **không có sẵn tài
khoản hoặc dữ liệu nhà hàng**. `src/main/resources/data.sql` hiện chứa SQL mẫu
đã comment; cấu hình Docker chủ động tắt tự động chạy SQL.

Sau khi backend đã tạo bảng, kiểm tra kết nối và số tài khoản từ đúng database
Docker. Với Docker Compose, chạy từ thư mục backend:

```powershell
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h 127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT DATABASE(); SELECT COUNT(*) AS accounts FROM account;"'
```

Nếu đây là database demo mới và các bảng nghiệp vụ còn trống, nạp dữ liệu mẫu
một lần:

```powershell
docker compose --env-file .env.docker --profile demo run --rm seed-demo
```

Nếu dùng script standalone, nạp file Docker seed sau khi backend đã tạo bảng
và bạn đã kiểm tra database demo còn trống:

```powershell
docker cp ./docker/seed-demo.mysql restaurant-mysql:/tmp/restaurant-seed-demo.sql
docker exec restaurant-mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 --protocol=TCP -h 127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/restaurant-seed-demo.sql'
```

Đăng nhập web demo với mã nhà hàng `NH001`, tên đăng nhập `admin` và mật khẩu
`PhoVietDemo!26`. Tài khoản MySQL trong `.env.docker` dùng cho kết nối database.

Để dùng dữ liệu hiện có, xuất một database bằng MySQL Workbench/mysqldump thành
`restaurant.sql`. Dùng bản export của đúng project, không chứa `CREATE DATABASE`
hoặc `USE` trỏ sang database khác. Chỉ import vào database Docker local mới/rỗng;
dump có `DROP TABLE` sẽ thay thế bảng hiện tại. Giữ bản export gốc làm backup.

Dừng ứng dụng trong lúc import để tránh ghi dữ liệu đồng thời:

```powershell
docker compose --env-file .env.docker stop frontend backend
docker compose --env-file .env.docker cp .\restaurant.sql mysql:/tmp/restaurant.sql
docker compose --env-file .env.docker exec mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE" < /tmp/restaurant.sql'
docker compose --env-file .env.docker exec mysql rm /tmp/restaurant.sql
docker compose --env-file .env.docker up -d --wait --wait-timeout 300
```

Không cần đổi database đang chạy ngoài Docker. Dữ liệu Docker nằm trong volume
`restaurant-local_mysql_data`. `stop` hoặc `down` giữ lại volume; tránh `down -v`
vì thao tác đó xóa dữ liệu. Mật khẩu/tên database trong `.env.docker` chỉ tạo tài
khoản/schema khi volume MySQL được khởi tạo lần đầu; sửa file không tự đổi mật
khẩu hoặc tên database đã tồn tại.

### 4. Tính năng cần cấu hình thêm

- **Upload ảnh:** thay `CLOUDINARY_URL` local bằng thông tin Cloudinary thật.
  Placeholder chỉ giúp ứng dụng khởi động, không upload được ảnh.
- **AI:** đặt `GEMINI_API_KEY`, và model mà tài khoản của bạn sử dụng được.
- **SePay/Discord:** đặt các biến tương ứng nếu sử dụng; webhook từ bên ngoài cần
  URL HTTPS công khai và secret riêng.
- **QR + vị trí trên điện thoại:** `http://localhost:8088` phục vụ kiểm tra trên
  máy tính. Điện thoại cần URL HTTPS tin cậy để cấp vị trí; đổi bind sang
  `0.0.0.0` chỉ mở truy cập LAN, không tạo HTTPS.
- **FE:** `VITE_API_BASE_URL=/api/v1`, `VITE_WS_URL=/ws` được truyền lúc build,
  không phải runtime secret. Đổi build args thì build lại image frontend.

### 5. Cập nhật, xem log và dừng

```powershell
# Sau khi cập nhật code
docker compose --env-file .env.docker up -d --build --wait --wait-timeout 300

# Xem log; Ctrl+C thoát khỏi việc theo dõi log, không dừng container
docker compose --env-file .env.docker logs -f --tail=100 backend mysql frontend

# Dừng nhưng giữ container/dữ liệu
docker compose --env-file .env.docker stop

# Gỡ container/network; vẫn giữ database volume
docker compose --env-file .env.docker down
```

Nếu backend unhealthy, xem log backend/MySQL trước. Nếu báo thiếu JWT secret,
điền `.env.docker`. Nếu Docker báo không kết nối được daemon, mở Docker Desktop
và chờ engine sẵn sàng. Nếu truy vấn MySQL báo access denied sau khi sửa mật khẩu,
đồng bộ với tài khoản trong volume hiện có thay vì xóa dữ liệu.

### Lưu cấu hình lên Git

Commit `Dockerfile`, `.dockerignore`, `nginx.conf`, `start-docker.ps1`, README và code liên quan ở
từng repo. `compose.yaml` và `.env.docker.example` nằm trong repo backend, nên
không bị bỏ quên ở thư mục cha không có Git. Không commit `.env.docker` hoặc dump
database. Dockerfile backend chỉ build JAR; trước khi phát hành vẫn phải chạy
test, ví dụ `./gradlew test bootJar` với Java 21.

## CI/CD backend

Workflow `.github/workflows/cicd.yml` chạy khi push vào các nhánh `main`, `dev`,
`develop`, `tai-dev`, `trung-dev`, hoặc mở/cập nhật pull request có nhánh đích
thuộc danh sách trên. Có thể chạy thủ công bằng Actions → Run workflow.

CI checkout source, cài Temurin Java 21 kèm Gradle cache, cấp quyền cho Gradle
Wrapper rồi chạy `./gradlew --no-daemon --console=plain clean build`. Profile
`test` dùng H2, không cần mật khẩu database local hoặc production cho bước CI.
Workflow không chạy Spotless và
không tự tạo commit hoặc push thay đổi format lên nhánh.

Spotless vẫn có sẵn để chạy thủ công trên máy local khi cần:
`.\gradlew.bat spotlessApply "-PformatBase=origin/main"`.

Sau khi `main` đạt bước test và build, workflow build backend image, chạy thử với MySQL
tạm và kiểm tra `/actuator/health/readiness`. Image được phát hành lên
`ghcr.io/thanhtrung237/restaurant-backend` với tag `latest` và `sha-<commit-sha>`.

### Triển khai trên Docker Desktop

Mở PowerShell ở repo backend khi MySQL của stack Compose đã healthy. Dùng
tag SHA của image cần triển khai. Với package riêng tư, đăng nhập GHCR trên
máy Docker bằng tài khoản có quyền pull.

```powershell
.\deploy\deploy-backend.ps1 -Image ghcr.io/thanhtrung237/restaurant-backend:sha-<commit-sha>
```

Thay `<commit-sha>` bằng SHA thực tế. Script chỉ cập nhật service `backend`
và chờ readiness. Có thể dùng tag SHA cũ để chạy lại image phiên bản trước.

Với repo GitHub riêng tư, đặt Actions variable `RESTAURANT_BACKEND_DIR` thành
đường dẫn tuyệt đối đến checkout backend chứa `compose.yaml` và `.env.docker`.
Đăng ký runner Windows với nhãn `restaurant-backend-deploy` trên máy Docker.
Tài khoản chạy runner cần truy cập được Docker Desktop và GHCR. Khi đó job
triển khai tự chạy sau bước phát hành trên `main`. Repo công khai dùng lệnh
PowerShell ở trên.
