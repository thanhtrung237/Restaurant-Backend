package com.group.restaurant.models;

import com.group.restaurant.enums.ApplyLevel;
import com.group.restaurant.enums.PromotionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDate;

@Entity
@Table(name = "promotion")
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Promotion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_promotion_restaurant"))
    private Restaurant restaurant;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "time_window", length = 100)
    private String timeWindow;

    @Column(name = "days_of_week", length = 50)
    private String daysOfWeek;

    @Column(name = "apply_level", nullable = false)
    private ApplyLevel applyLevel;

    @Column(name = "`condition`", columnDefinition = "TEXT")
    private String condition;

    @Column(name = "action", columnDefinition = "TEXT")
    private String action;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "status", nullable = false)
    private PromotionStatus status;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private int usageCount = 0;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private int version = 0;
}
