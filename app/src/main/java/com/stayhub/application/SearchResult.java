package com.stayhub.application;

import com.stayhub.domain.StaySearchCriteria;
import java.util.List;

/**
 * 통합 검색 결과. failures 가 비어 있지 않으면 일부 공급사 결과가 빠진 부분 결과다.
 */
public record SearchResult(StaySearchCriteria criteria, List<SearchItem> items, List<SearchFailure> failures) {

    public SearchResult {
        items = List.copyOf(items);
        failures = List.copyOf(failures);
    }

    public boolean partial() {
        return !failures.isEmpty();
    }
}
