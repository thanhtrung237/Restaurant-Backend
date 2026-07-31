package com.group.restaurant.models;

import com.group.restaurant.enums.ApplyLevel;
import com.group.restaurant.enums.PromotionStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "promotion")
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
}
