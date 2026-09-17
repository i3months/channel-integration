package com.stayhub.application;

import java.util.ArrayList;
import java.util.List;

/**
 * 공급사 재고·요금 API 는 한 번에 최대 50개 숙소 코드만 받으므로 코드를 청크로 나눈다.
 */
public final class Chunks {

    private Chunks() {
    }

    public static <T> List<List<T>> split(List<T> items, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("chunk size must be positive: " + size);
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int from = 0; from < items.size(); from += size) {
            chunks.add(List.copyOf(items.subList(from, Math.min(from + size, items.size()))));
        }
        return List.copyOf(chunks);
    }
}
