package com.group.restaurant.dtos.response.report;

import com.group.restaurant.enums.AnomalySeverity;
import com.group.restaurant.enums.AnomalyType;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyAlert {
    private String id;
    private AnomalyType type;
    private AnomalySeverity severity;
    private String title;
    private String location;
    private String description;
    private String aiExplanation;
    private String recommendation;
    private LocalDateTime detectedAt;
    private Double anomalyScore;
    private Map<String, Object> metrics;
}
