package com.stayhub.domain;

/**
 * 매핑 저장소에 있는 객실 타입. id 가 내부 객실 타입 식별자다.
 * 공급사 객실 타입 코드는 숙소 안에서만 유일하므로 stayId 와 함께 식별한다.
 */
public record RoomType(long id, long stayId, String supplierRoomTypeCode, String name, int maxOccupancy, boolean active) {
}
