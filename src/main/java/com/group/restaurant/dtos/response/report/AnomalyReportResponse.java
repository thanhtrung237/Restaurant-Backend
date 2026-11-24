package com.group.restaurant.dtos.response.report;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyReportResponse {
    private int totalAnomalies;
    private int criticalCount;
    private int warningCount;
    private List<AnomalyAlert> alerts;
    private String aiExecutiveSummary;
    private LocalDateTime evaluatedAt;
}
