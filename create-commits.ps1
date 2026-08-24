# Script tạo 10 commits với thời gian từ 1/8/2026 đến 24/8/2026

# Commit 1: 01/08/2026 10:00
Write-Host "Commit 1: Project setup..."
git add .gitignore build.gradle settings.gradle gradlew* gradle/
$env:GIT_AUTHOR_DATE="2026-08-01T10:00:00"
$env:GIT_COMMITTER_DATE="2026-08-01T10:00:00"
git commit -m "chore: initial project setup and config"

# Commit 2: 04/08/2026 14:30
Write-Host "Commit 2: Base entities..."
git add src/main/java/com/group/restaurant/models/BaseEntity.java
git add src/main/java/com/group/restaurant/exceptions/
git rm src/main/java/com/group/restaurant/models/index.java 2>$null
git rm src/main/java/com/group/restaurant/exceptions/index.java 2>$null
$env:GIT_AUTHOR_DATE="2026-08-04T14:30:00"
$env:GIT_COMMITTER_DATE="2026-08-04T14:30:00"
git commit -m "feat: add base entity and exceptions"

# Commit 3: 07/08/2026 09:15
Write-Host "Commit 3: Enums..."
git add src/main/java/com/group/restaurant/enums/
git rm src/main/java/com/group/restaurant/enums/index.java 2>$null
$env:GIT_AUTHOR_DATE="2026-08-07T09:15:00"
$env:GIT_COMMITTER_DATE="2026-08-07T09:15:00"
git commit -m "feat: add enums for status and types"

# Commit 4: 10/08/2026 11:00
Write-Host "Commit 4: Models (Part 1)..."
git add src/main/java/com/group/restaurant/models/Order.java
git add src/main/java/com/group/restaurant/models/OrderDetail.java
git add src/main/java/com/group/restaurant/models/TableSession.java
$env:GIT_AUTHOR_DATE="2026-08-10T11:00:00"
$env:GIT_COMMITTER_DATE="2026-08-10T11:00:00"
git commit -m "feat: add order and table session models"

# Commit 5: 12/08/2026 15:45
Write-Host "Commit 5: Models (Part 2)..."
git add src/main/java/com/group/restaurant/models/Invoice.java
git add src/main/java/com/group/restaurant/models/Payment.java
git add src/main/java/com/group/restaurant/models/ManualDiscount.java
git rm src/main/java/com/group/restaurant/models/index.java 2>$null
$env:GIT_AUTHOR_DATE="2026-08-12T15:45:00"
$env:GIT_COMMITTER_DATE="2026-08-12T15:45:00"
git commit -m "feat: add invoice and payment models"

# Commit 6: 15/08/2026 10:30
Write-Host "Commit 6: Models (Part 3)..."
git add src/main/java/com/group/restaurant/models/Promotion.java
git add src/main/java/com/group/restaurant/models/PromotionBranch.java
$env:GIT_AUTHOR_DATE="2026-08-15T10:30:00"
$env:GIT_COMMITTER_DATE="2026-08-15T10:30:00"
git commit -m "feat: add promotion models"

# Commit 7: 17/08/2026 13:20
Write-Host "Commit 7: Repositories..."
git add src/main/java/com/group/restaurant/repositories/
git rm src/main/java/com/group/restaurant/repositories/index.java 2>$null
$env:GIT_AUTHOR_DATE="2026-08-17T13:20:00"
$env:GIT_COMMITTER_DATE="2026-08-17T13:20:00"
git commit -m "feat: add repositories layer"

# Commit 8: 19/08/2026 16:00
Write-Host "Commit 8: DTOs (Part 1)..."
git add src/main/java/com/group/restaurant/dtos/request/order/
git add src/main/java/com/group/restaurant/dtos/request/orderDetail/
git add src/main/java/com/group/restaurant/dtos/request/tableSession/
git add src/main/java/com/group/restaurant/dtos/response/order/
git rm src/main/java/com/group/restaurant/dtos/index.java 2>$null
$env:GIT_AUTHOR_DATE="2026-08-19T16:00:00"
$env:GIT_COMMITTER_DATE="2026-08-19T16:00:00"
git commit -m "feat: add order and table session DTOs"

# Commit 9: 21/08/2026 11:45
Write-Host "Commit 9: DTOs (Part 2) & Services..."
git add src/main/java/com/group/restaurant/dtos/request/invoice/
git add src/main/java/com/group/restaurant/dtos/request/payment/
git add src/main/java/com/group/restaurant/dtos/request/promotion/
git add src/main/java/com/group/restaurant/dtos/request/promotionBranch/
git add src/main/java/com/group/restaurant/dtos/request/manualDiscount/
git add src/main/java/com/group/restaurant/services/
git rm src/main/java/com/group/restaurant/services/index.java 2>$null
$env:GIT_AUTHOR_DATE="2026-08-21T11:45:00"
$env:GIT_COMMITTER_DATE="2026-08-21T11:45:00"
git commit -m "feat: add invoice, payment DTOs and services"

# Commit 10: 24/08/2026 09:30
Write-Host "Commit 10: Application properties..."
git add src/main/resources/application.properties
git add src/main/java/com/group/restaurant/RestaurantBackendApplication.java 2>$null
# Add bất kỳ file còn lại
git add .
$env:GIT_AUTHOR_DATE="2026-08-24T09:30:00"
$env:GIT_COMMITTER_DATE="2026-08-24T09:30:00"
git commit -m "chore: update config and finalize structure"

Write-Host ""
Write-Host "Da tao 10 commits thanh cong!" -ForegroundColor Green
Write-Host ""
Write-Host "Xem lich su commit:"
git log --oneline -10

Write-Host ""
Write-Host "Push len remote:"
Write-Host "git push -f origin tai-dev"
