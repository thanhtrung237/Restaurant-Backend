package com.group.restaurant.models;

import com.group.restaurant.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "table_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_order_table_session"))
        private TableSession tableSession;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "ordered_by", foreignKey = @ForeignKey(name = "fk_order_account"))
        private Account orderedBy;

        @Column(name = "note", columnDefinition = "TEXT")
        private String note;

        @Column(name = "status", nullable = false)
        @Builder.Default
        private OrderStatus status = OrderStatus.PENDING;

        @OneToMany(mappedBy = "order", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
        @Builder.Default
        private List<OrderDetail> orderDetails = new ArrayList<>();
}
