package com.group.restaurant.services;

import com.group.restaurant.dtos.response.report.AnomalyReportResponse;
import java.time.LocalDateTime;
import java.util.List;

public interface AnomalyDetectionService {
    AnomalyReportResponse detectAnomalies(
            List<Long> branchIds, LocalDateTime from, LocalDateTime to);
}
