package com.group.restaurant.models;

import com.group.restaurant.enums.InvoiceStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoice", uniqueConstraints = {
        @UniqueConstraint(name = "uk_invoice_branch_number", columnNames = {"branch_id", "invoice_number"}) // BR-19
})
@SQLRestriction("is_deleted = false")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invoice extends BaseEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "branch_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invoice_branch"))
        private Branch branch;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "table_session_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invoice_table_session"))
        private TableSession tableSession;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "promotion_id", foreignKey = @ForeignKey(name = "fk_invoice_promotion"))
        private Promotion promotion;
        @OneToOne
        @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(name = "fk_invoice_order"))
        private Order order;

        @Column(name = "invoice_number", nullable = false, length = 50)
        private String invoiceNumber;

        @Column(name = "invoice_note", columnDefinition = "TEXT")
        private String invoiceNote;

        @Column(name = "sub_total_before_discount", precision = 14, scale = 2)
        private BigDecimal subTotalBeforeDiscount;

        // BR-13: khuyến mãi tự động (cấp món + cấp hóa đơn) — tách riêng với chiết khấu thủ công
        @Column(name = "total_promotion_discount", precision = 14, scale = 2)
        private BigDecimal totalPromotionDiscount;

        @Column(name = "total_manual_discount", precision = 14, scale = 2)
        private BigDecimal totalManualDiscount;

        @Column(name = "service_fee", precision = 14, scale = 2)
        private BigDecimal serviceFee;

        @Column(name = "vat_amount", precision = 14, scale = 2)
        private BigDecimal vatAmount;

        @Column(name = "grand_total", nullable = false, precision = 14, scale = 2)
        private BigDecimal grandTotal;

        @Column(name = "status", nullable = false)
        @Builder.Default
        private InvoiceStatus status = InvoiceStatus.PENDING;

        @OneToMany(mappedBy = "invoice", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
        @Builder.Default
        private List<Payment> payments = new ArrayList<>();
}
