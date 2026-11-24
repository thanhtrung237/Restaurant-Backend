package com.group.restaurant.services.impl;

import com.group.restaurant.models.Branch;
import com.group.restaurant.repositories.BranchRepository;
import com.group.restaurant.services.BranchDishStockService;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockResetScheduler {

    private final BranchRepository branchRepository;
    private final BranchDishStockService branchDishStockService;

    @Scheduled(cron = "0 * * * * *")
    public void resetStockForBranchesAtBusinessDayStart() {
        LocalTime now = LocalTime.now().withSecond(0).withNano(0);

        List<Branch> branches = branchRepository.findAll();
        for (Branch branch : branches) {
            LocalTime resetTime = branch.getBusinessDayStartTime();

            // Nếu chi nhánh chưa cấu hình giờ bắt đầu → bỏ qua
            if (resetTime == null) {
                continue;
            }

            // So sánh giờ:phút (bỏ giây/nano)
            LocalTime resetHourMinute = resetTime.withSecond(0).withNano(0);
            if (now.equals(resetHourMinute)) {
                int count = branchDishStockService.resetStockForBranch(branch.getId());
                if (count > 0) {
                    log.info(
                            "[BR-06] Auto-reset {} dishes to AVAILABLE for branch {} ({})",
                            count,
                            branch.getId(),
                            branch.getName());
                }
            }
        }
    }
}
