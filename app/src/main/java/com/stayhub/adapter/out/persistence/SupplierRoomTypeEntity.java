package com.stayhub.adapter.out.persistence;

import com.stayhub.domain.RoomType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 공급사 객실 타입 코드는 숙소 안에서만 유일하므로 (stay_id, supplier_room_type_code) 로 유일하다.
 * stay_id 가 공급사와 숙소 코드를 대표하므로 결과적으로 세 값 키가 된다.
 */
@Entity
@Table(
        name = "supplier_room_type",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_supplier_room_type_code",
                columnNames = {"stay_id", "supplier_room_type_code"}))
public class SupplierRoomTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stay_id", nullable = false, foreignKey = @ForeignKey(name = "fk_room_type_stay"))
    private SupplierStayEntity stay;

    @Column(name = "supplier_room_type_code", nullable = false, length = 64)
    private String supplierRoomTypeCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "max_occupancy", nullable = false)
    private int maxOccupancy;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SupplierRoomTypeEntity() {
    }

    public SupplierRoomTypeEntity(SupplierStayEntity stay, String supplierRoomTypeCode, String name, int maxOccupancy) {
        this.stay = stay;
        this.supplierRoomTypeCode = supplierRoomTypeCode;
        this.name = name;
        this.maxOccupancy = maxOccupancy;
        this.active = true;
    }

    void refresh(String name, int maxOccupancy) {
        this.name = name;
        this.maxOccupancy = maxOccupancy;
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

    RoomType toDomain() {
        return new RoomType(id, stay.getId(), supplierRoomTypeCode, name, maxOccupancy, active);
    }

    public Long getId() {
        return id;
    }

    public String getSupplierRoomTypeCode() {
        return supplierRoomTypeCode;
    }

    public boolean isActive() {
        return active;
    }
}
