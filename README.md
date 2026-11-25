# Restaurant-Backend

## Docker local: hai nhóm riêng cho BE và FE

Docker Desktop hiển thị hai nhóm Compose, tổng cộng ba container riêng:

```text
restaurant-service
  backend-1    Spring Boot
  mysql-1      MySQL 8.4
restaurant-client
  frontend-1   React / Nginx
```

Đặt hai repo BE/FE cạnh nhau. Mở Docker Desktop và chạy PowerShell tại repo BE:

```powershell
powershell -ExecutionPolicy Bypass -File .\start-docker.ps1 -Build
```

Script dùng Node.js đã cài cho FE. Khi chuyển từ cấu hình local cũ, script build
trước, backup vào `.docker-runtime/compose-groups/`, dừng container cũ và dùng
lại volume `MYSQL_VOLUME_NAME`. Sau khi container mới healthy, script đối chiếu
số dòng từng bảng rồi mới xóa container cũ. Volume và dữ liệu được giữ lại;
không thay đổi EC2. Cấu hình gộp Java và MySQL trong một container đã được bỏ.

| Thành phần | Địa chỉ mặc định |
| --- | --- |
| FE / đăng nhập | http://localhost:8088/dang-nhap |
| BE / Swagger | http://localhost:8081/swagger-ui/index.html |
| MySQL Workbench | `127.0.0.1:3307` |

Database, username và password lấy từ `.env.docker` hiện tại. Không chép file
mẫu đè lên cấu hình đang có. Nếu cài mới, sao chép `.env.docker.example` thành
`.env.docker`, điền hai mật khẩu MySQL và hai JWT secret Base64 khác nhau (mỗi
khóa ít nhất 32 byte ngẫu nhiên) trước khi chạy script. Có thể tạo từng khóa
bằng `node -e "console.log(require('node:crypto').randomBytes(64).toString('base64'))"`.
Database mới rỗng cần import dữ liệu riêng; không tự có dữ liệu từ MySQL local khác.

Hai nhóm dùng chung network external `restaurant-shared`. FE giữ API `/api/v1`
và SockJS `/ws`, Nginx chuyển tới `backend:8080` trên network chung. BE kết nối
MySQL qua `mysql:3306`; mỗi dịch vụ vẫn ở container riêng.

Sau khi sửa code, có thể cập nhật từng phần; cập nhật BE không restart MySQL:

```powershell
.\start-docker.ps1 -Service backend -Build -Recreate
.\start-docker.ps1 -Service frontend -Build -Recreate
```

Các lệnh thủ công sau dùng khi network/volume đã được script chuẩn bị:

```powershell
# Chạy trong Restaurant-Backend: nhóm BE + DB
docker compose --env-file .env.docker up -d --build --wait --wait-timeout 600
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs -f backend mysql

# Cũng từ Restaurant-Backend: nhóm FE riêng
docker compose --env-file .env.docker -f ../Restaurant-Frontend/compose.yaml up -d --build --wait
docker compose --env-file .env.docker -f ../Restaurant-Frontend/compose.yaml ps
docker compose --env-file .env.docker -f ../Restaurant-Frontend/compose.yaml logs -f frontend

# Dừng FE trước, sau đó BE/DB; dữ liệu vẫn ở volume
docker compose --env-file .env.docker -f ../Restaurant-Frontend/compose.yaml stop
docker compose --env-file .env.docker stop
```

Không dùng lệnh Compose cũ khi container cũ đang giữ volume; dùng script để
chuyển lần đầu. Volume database là external; không xóa volume hoặc dùng
`down -v` để cập nhật ứng dụng. Backup của các lần chuyển trước vẫn được giữ.
MySQL image được kiểm tra khớp image đang dùng trước khi chuyển, không tự nâng
phiên bản DB. Khi cần pin phiên bản, đặt `MYSQL_IMAGE=mysql@sha256:<digest>`
trong `.env.docker`. Có thể đổi network qua `RESTAURANT_NETWORK_NAME`.

Cấu hình EC2 ở `deploy/ec2/` vẫn là cấu hình server riêng. Các bước dưới đây
về EC2 không thay đổi Docker local thành cấu hình production.

## Deploy lên AWS EC2 (Ubuntu x86_64)

Bộ cấu hình server nằm trong `deploy/ec2/`, tách khỏi Docker local. Có đúng ba
container FE, BE và MySQL; HTTPS sau này do Caddy chạy trên host EC2 đảm nhiệm.
Các image được pull từ GHCR, không build Java/Node trên EC2.

### Tạo EC2 lần đầu

EC2 backend mới dùng `deploy/ec2/compose.backend.yaml` (chỉ BE và MySQL),
copy lên server thành `~/restaurant-ec2/compose.yaml`. Caddy chạy trên host;
`deploy/ec2/Caddyfile.swagger-ip` phục vụ Swagger tại
`http://47.129.142.237/swagger-ui/index.html`. BE vẫn bind loopback 8081,
MySQL không publish port. Swagger yêu cầu `SWAGGER_ENABLED=true`.

Khi DNS `restaurant.thanhtrung.click` đã trỏ về EC2, dùng
`deploy/ec2/Caddyfile.backend` cho `/etc/caddy/Caddyfile` để có Swagger HTTPS:
`https://restaurant.thanhtrung.click/swagger-ui/index.html`. File này cũng giữ
đường dẫn IP HTTP. FE Vercel dùng
`VITE_API_BASE_URL=https://restaurant.thanhtrung.click/api/v1` và
`VITE_WS_URL=https://restaurant.thanhtrung.click/ws`; đổi env cần Redeploy FE.

Server mới đã nhận dữ liệu từ Docker local theo lựa chọn của người dùng:
28 bảng, 142 dòng tại thời điểm chuyển, đối chiếu từng bảng sau khi BE khởi động.
Database EC2 cũ không bị thay đổi. Backup trước khi nhập giữ trên EC2 trong
`~/restaurant-ec2/backups/`. Mật khẩu DB/JWT của máy mới được tạo riêng và lưu
ở `.env.ec2` trên server (quyền 600), không commit lên Git.
`deploy/ec2/install-backend-host.sh` cài Docker/Caddy trên Ubuntu. Bộ cấu hình
ba container FE/BE/MySQL bên dưới vẫn dành cho máy EC2 cũ.

MySQL Workbench kết nối database mới bằng **Standard TCP/IP over SSH**:
SSH Hostname `47.129.142.237:22`, SSH Username `ubuntu`, SSH Key File là bản key
`deploy/ec2/.runtime/keybeupdate.private.pem` đã bảo vệ quyền truy cập trên máy bạn.
MySQL Hostname `127.0.0.1`, MySQL Server Port `3307`, Username `restaurant`,
Default Schema `restaurantServiceDB`. Password lấy từ `MYSQL_PASSWORD` của
`.env.ec2` trên server hoặc bản cấu hình riêng tại
`deploy/ec2/.runtime/new-backend/.env.ec2`; bỏ dấu nháy ngoài giá trị.
Không dùng MYSQL_ROOT_PASSWORD hoặc password database local. Cổng 3307 chỉ bind
loopback trên EC2, không thêm rule public 3306/3307 vào Security Group.

Trong AWS Console → EC2 → Launch instance:

- Region gợi ý: Singapore (`ap-southeast-1`).
- AMI: Ubuntu Server 24.04 LTS, **64-bit x86**, không chọn ARM/Graviton cho các
  image amd64 hiện tại.
- Instance gợi ý cho cả Java và MySQL: `t3.medium` (2 vCPU, 4 GiB RAM).
- Storage: gp3 khoảng 40 GiB, bật encryption.
- Key pair: tạo key RSA, lưu file `.pem` trên máy, ngoài Git repo.
- Network: public subnet có route Internet Gateway, bật public IPv4.
- Security Group inbound: SSH TCP 22 từ **My IP**; HTTP 80 và HTTPS 443 từ
  Internet. Không mở 3306/3307 hoặc cổng backend 8080/8081 ra Internet.
- Xem chi phí trước khi Launch và kiểm tra Billing/Free Tier/Budgets. EC2, EBS,
  public IPv4 và lưu lượng có thể tính phí; không mặc định coi EC2 là miễn phí.

Hướng dẫn AWS:
https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/tutorial-launch-my-first-ec2-instance.html

### SSH và cài Docker Engine

Trong PowerShell local, thay đường dẫn key và IP thực tế:

```powershell
ssh -i "C:\Users\Tan Tai\Downloads\restaurant-ec2.pem" ubuntu@EC2_PUBLIC_IP
```

Đối chiếu SSH host fingerprint với thông tin instance khi kết nối lần đầu.
Trong SSH Ubuntu, cài Docker theo apt repository chính thức:

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu
exit
```

SSH lại để nhận group mới, rồi kiểm tra `docker version`, `docker compose version`.
Tài liệu: https://docs.docker.com/engine/install/ubuntu/

### Chuyển cấu hình và chọn image đã publish

Ưu tiên dùng bản publish thành công trên `main` của cả hai repo. Lấy tag
`sha-<commit>` từ các job publish; SHA BE và FE có thể khác nhau. Cấu hình EC2
không tự thấy image đang nằm trên Docker Desktop.

Nếu bản đầu tiên được chuyển trực tiếp qua SSH bằng `docker save`/`docker load`,
đặt `BACKEND_IMAGE` và `FRONTEND_IMAGE` đúng các tag đã load trên EC2, rồi chạy
`bash deploy.sh all --skip-pull`. Chế độ này không cần công khai image hoặc token
GHCR; khi cập nhật tiếp cần chuyển image mới hoặc đổi sang các tag GHCR hợp lệ.
Không bỏ `--skip-pull` trong lúc env vẫn dùng các tag local này.

Trong PowerShell ở repo backend:

```powershell
$ec2Key = 'C:\Users\Tan Tai\Downloads\restaurant-ec2.pem'
$ec2Host = 'EC2_PUBLIC_IP'
ssh -i $ec2Key "ubuntu@$ec2Host" 'mkdir -p ~/restaurant-ec2 && chmod 700 ~/restaurant-ec2'
scp -i $ec2Key deploy/ec2/compose.yaml deploy/ec2/.env.example deploy/ec2/nginx.conf.template deploy/ec2/deploy.sh deploy/ec2/backup-db.sh deploy/ec2/Caddyfile.example "ubuntu@${ec2Host}:restaurant-ec2/"
```

Trong SSH Ubuntu:

```bash
cd ~/restaurant-ec2
sed -i 's/\r$//' deploy.sh backup-db.sh
chmod +x deploy.sh backup-db.sh
umask 077
cp -n .env.example .env.ec2
nano .env.ec2
```

Điền image GHCR, public IP, database name và credentials. Tạo hai mật khẩu khác
nhau bằng `openssl rand -hex 24`, hai JWT secret khác nhau bằng
`openssl rand -base64 64 | tr -d '\n'; echo`. Đặt Cloudinary credentials thật nếu
cần upload ảnh. Không gửi `.pem`, AWS secret key hoặc file env thật lên Git.

Image private cần login GHCR trên EC2 bằng tài khoản có quyền đọc package và
token `read:packages`. Chạy `docker login ghcr.io -u GITHUB_USERNAME`, nhập token
tại password prompt. Tài khoản GitHub phải được cấp quyền đọc cả hai package.

### Khởi tạo database và chạy bằng public IP

Export **cấu trúc và dữ liệu từ MySQL Docker 8.4 đang chạy local**, không lấy
thẳng dump từ MySQL 9.4 nếu chưa kiểm tra tương thích. Không chọn Include Create
Schema; nếu dump có `USE`, tên đó phải khớp `MYSQL_DATABASE` trên EC2, kể cả chữ
hoa/thường. Copy dump riêng bằng SCP tới `~/restaurant-ec2/restaurant.sql`.

Trong SSH Ubuntu:

```bash
cd ~/restaurant-ec2
docker compose --env-file .env.ec2 -f compose.yaml config --quiet
docker compose --env-file .env.ec2 -f compose.yaml up -d --wait --wait-timeout 300 mysql

# Chỉ import vào database EC2 mới/rỗng. Dump có DROP TABLE sẽ thay bảng hiện có.
docker compose --env-file .env.ec2 -f compose.yaml exec -T mysql sh -ec 'export MYSQL_PWD="$MYSQL_PASSWORD"; exec mysql --default-character-set=utf8mb4 -u"$MYSQL_USER" "$MYSQL_DATABASE"' < restaurant.sql

bash deploy.sh all
```

Backend mặc định `ddl-auto=validate`: schema cần tồn tại và khớp model trước
khi BE healthy. Nếu validate báo thiếu cột, chuẩn bị migration phù hợp trước,
không chuyển sang `create`/`create-drop` trên database đã nhập dữ liệu.

Mở `http://EC2_PUBLIC_IP`. FE/API hoạt động qua cổng 80; MySQL không public, BE
chỉ bind loopback. Đây là bước kiểm tra kết nối ban đầu; geolocation trên điện
thoại cần HTTPS. Dữ liệu EC2 dùng volume `restaurant-production_mysql_data`,
không dùng chung volume với Docker local.

### Thêm HTTPS khi đã có tên miền

1. Trỏ DNS A của hostname về public IPv4 EC2. IP public thông thường có thể đổi
   khi stop/start EC2; Elastic IP giúp cố định nhưng cũng có thể phát sinh phí.
2. Cài Caddy trên Ubuntu theo https://caddyserver.com/docs/install#debian-ubuntu-raspbian.
   Dừng Caddy trong lúc chuyển frontend khỏi cổng 80.
3. Đổi `.env.ec2`: `APP_PUBLIC_ORIGIN=https://TEN_MIEN`,
   `HTTP_BIND_ADDRESS=127.0.0.1`, `FRONTEND_PORT=8088`, `TRUST_PROXY_HEADERS=1`.
4. Chạy `bash deploy.sh all` để áp dụng origin và port mới cho BE/FE.
5. Thay hostname trong `Caddyfile.example`, copy vào `/etc/caddy/Caddyfile`, chạy
   `sudo caddy validate --config /etc/caddy/Caddyfile` rồi `sudo systemctl restart caddy`.
6. Kiểm tra đăng nhập, QR/vị trí trên điện thoại, WebSocket, upload ảnh và SePay
   bằng domain HTTPS. Cập nhật URL webhook SePay nếu sử dụng.

Nginx server chỉ tin forwarded headers khi `TRUST_PROXY_HEADERS=1`. Chỉ bật
chế độ này khi frontend đã bind loopback sau Caddy. Swagger tắt mặc định; nếu
bật để debug thì xem xét giới hạn quyền truy cập trước khi public.

### Cập nhật, rollback và backup

```bash
# Đổi đúng tag SHA trong .env.ec2, sau đó cập nhật service cần thiết
bash deploy.sh backend
bash deploy.sh frontend

docker compose --env-file .env.ec2 -f compose.yaml logs -f --tail=100 backend
bash backup-db.sh
```

Với image đã chuyển bằng SSH, thêm `--skip-pull` sau `all`, `backend` hoặc
`frontend`. `docker load` phải hoàn tất trước khi gọi deploy.

Script deploy có lock và chỉ thay app container, không recreate/upgrade MySQL.
Lưu lại tag SHA đang chạy trước khi sửa env. Khi cần rollback app, phục hồi tag
SHA cũ rồi deploy lại; rollback image không tự hoàn tác migration database.
File backup được lưu riêng tư trong `backups/`; copy backup ra ngoài EC2 (ví dụ
máy local hoặc S3) và thử restore. Không dùng `down -v` để cập nhật ứng dụng.

Xem phần [Tự động triển khai BE lên EC2](#tự-động-triển-khai-be-lên-ec2-qua-ssh) để bật CD qua SSH.

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

### Tự động triển khai BE lên EC2 qua SSH

Push nhánh `deploy` chạy CI, build image, smoke test với MySQL, push image lên
Docker Hub rồi SSH vào EC2 để cập nhật BE. Docker Hub có tag `latest` và
`sha-<commit>`. EC2 lấy đúng digest do workflow xuất bản. Khi readiness hoặc
HTTPS `/ws/info` thất bại, script thử khôi phục image BE trước đó.
Job publish GHCR của nhánh `main` vẫn phục vụ các image phát hành hiện có.

1. Tạo repository `restaurant-backend` trong tài khoản Docker Hub. Tạo access
   token có quyền Read & Write để GitHub Actions push image.
2. Trong GitHub repo Backend → **Settings → Secrets and variables → Actions →
   Secrets**, tạo các secret sau.

   | Secret | Giá trị |
   | --- | --- |
   | `DOCKER_USERNAME` | Tên tài khoản Docker Hub |
   | `DOCKER_PASSWORD` | Access token có quyền Read & Write |
   | `EC2_HOST` | `47.129.142.237` hoặc Public IPv4 hiện tại của EC2 |
   | `EC2_SSH_KEY` | Nội dung private key SSH có quyền đăng nhập user `ubuntu` |
   | `EC2_KNOWN_HOSTS` | Dòng known_hosts đã xác minh của host dùng trong `EC2_HOST` |

   Với IP trên, bản ghi SSH đã xác minh nằm trong file local
   `deploy/ec2/.runtime/known_hosts.new-backend`. Dùng key riêng dành cho CD
   nếu có thể. Chỉ nhập khóa và token vào GitHub Secrets.
3. EC2 Security Group cần cho phép SSH TCP 22 từ runner. Nếu SSH đang giới hạn
   IP máy bạn, runner GitHub mặc định có thể bị timeout. Có thể cấu hình
   repository variable `EC2_SSH_RUNNER` bằng nhãn duy nhất của một runner Linux
   có đường SSH được phép. Giá trị mặc định là `ubuntu-24.04`.
   Giới hạn quyền push vào `deploy` cho người được phép phát hành.
4. Cài `deploy/ec2/deploy-release.sh` vào
   `/home/ubuntu/restaurant-ec2/deploy-release.sh`. Đăng nhập SSH và chạy
   `bash ~/restaurant-ec2/deploy-release.sh --check`. Nếu Docker Hub repository
   là private, chạy `docker login -u TEN_DOCKER_HUB` trên EC2 bằng user `ubuntu`
   với token chỉ có quyền đọc.
5. Commit cấu hình rồi push nhánh `deploy` từ commit muốn phát hành. Trong GitHub
   **Actions**, kiểm tra **Build & Test Spring Boot → Build and push to Docker Hub
   → Deploy backend to EC2 via SSH**. Xem log `Deployed` và thử API tại
   `https://restaurant.thanhtrung.click`.

Mỗi lần phát hành giữ MySQL chạy và lưu bản cấu hình trước ở
`~/restaurant-ec2/.runtime/env-before-release`. Giữ image BE cũ trên EC2 để
rollback. Nếu cần rollback thủ công sau khi deploy thành công, đặt
`BACKEND_IMAGE` về image ở dòng `Previous image` trong log Actions rồi chạy:

```bash
cd /home/ubuntu/restaurant-ec2
docker compose --env-file .env.ec2 -f compose.yaml up -d --no-deps --no-build --pull never --wait backend
```

Khi cần đổi schema MySQL, chuẩn bị migration tương thích với phiên bản cần
rollback. Xem [Docker Hub access tokens](https://docs.docker.com/security/access-tokens/)
và [mạng GitHub-hosted runners](https://docs.github.com/en/actions/reference/runners/github-hosted-runners).
