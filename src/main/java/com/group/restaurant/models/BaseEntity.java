package com.group.restaurant.models;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "create_by", updatable = false)
    private String createBy;

    @LastModifiedDate
    @Column(name = "modified_at")
    private LocalDateTime modifiedAt;

    @LastModifiedBy
    @Column(name = "modified_by")
    private String modifiedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @PrePersist
    protected void onPrePersist() {
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    /**
     * Bù `deletedBy`/`deletedAt` cho các lời gọi {@code softDelete(null)} rải khắp
     * tầng service (30 chỗ) — thay vì sửa từng chỗ, lấy luôn người thao tác mà
     * {@code AuditingEntityListener} vừa ghi vào `modifiedBy`.
     *
     * Theo đặc tả JPA, callback của entity listener (`AuditingEntityListener`) chạy TRƯỚC
     * callback khai báo trong chính entity, nên tới đây `modifiedBy` đã có giá trị
     * (xem {@code JpaAuditingConfig#auditorProvider}).
     */
    @PreUpdate
    protected void onPreUpdate() {
        if (Boolean.TRUE.equals(this.isDeleted)) {
            if (this.deletedBy == null) {
                this.deletedBy = this.modifiedBy;
            }
            if (this.deletedAt == null) {
                this.deletedAt = LocalDateTime.now();
            }
        }
    }

    public void softDelete(String deletedBy) {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedBy;
    }
}
