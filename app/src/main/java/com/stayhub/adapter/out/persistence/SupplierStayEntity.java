package com.stayhub.adapter.out.persistence;

import com.stayhub.domain.Stay;
import com.stayhub.domain.SupplierCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "supplier_stay",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_supplier_stay_code",
                columnNames = {"supplier_code", "supplier_hotel_code"}))
public class SupplierStayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Hibernate 6 는 EnumType.STRING 을 H2 에서 enum('A','B') 컬럼으로 만든다.
     * 그러면 공급사를 추가할 때마다 컬럼 타입을 바꿔야 하므로 VARCHAR 로 고정한다.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "supplier_code", nullable = false, length = 16)
    private SupplierCode supplierCode;

    @Column(name = "supplier_hotel_code", nullable = false, length = 64)
    private String supplierHotelCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SupplierStayEntity() {
    }

    public SupplierStayEntity(SupplierCode supplierCode, String supplierHotelCode, String name) {
        this.supplierCode = supplierCode;
        this.supplierHotelCode = supplierHotelCode;
        this.name = name;
        this.active = true;
    }

    /** 공급사 목록에 다시 나타났을 때. 이름을 최신으로 맞추고 활성 상태로 되돌린다. */
    void refresh(String name) {
        this.name = name;
        this.active = true;
    }

    void deactivate() {
        this.active = false;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    Stay toDomain() {
        return new Stay(id, supplierCode, supplierHotelCode, name, active);
    }

    public Long getId() {
        return id;
    }

    public SupplierCode getSupplierCode() {
        return supplierCode;
    }

    public String getSupplierHotelCode() {
        return supplierHotelCode;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
