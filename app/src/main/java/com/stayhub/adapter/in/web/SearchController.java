package com.stayhub.adapter.in.web;

import com.stayhub.application.SearchStaysService;
import com.stayhub.domain.StaySearchCriteria;
import io.swagger.v3.oas.annotations.Operation;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {

    static final int MAX_NIGHTS = 30;

    private final SearchStaysService service;

    public SearchController(SearchStaysService service) {
        this.service = service;
    }

    @Operation(summary = "날짜와 인원으로 보유 숙소 전체를 공급사 병렬 조회해 통합 검색한다")
    @GetMapping("/api/v1/stays/search")
    public SearchResponse search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam int adults,
            @RequestParam(defaultValue = "0") int children) {
        StaySearchCriteria criteria = new StaySearchCriteria(checkIn, checkOut, adults, children);
        validate(criteria);
        return SearchResponse.from(service.search(criteria));
    }

    private static void validate(StaySearchCriteria criteria) {
        if (!criteria.checkOut().isAfter(criteria.checkIn())) {
            throw new InvalidSearchRequestException("INVALID_DATE_RANGE", "checkOut must be after checkIn");
        }
        if (criteria.nights() > MAX_NIGHTS) {
            throw new InvalidSearchRequestException("INVALID_DATE_RANGE",
                    "stay length must be " + MAX_NIGHTS + " nights or less");
        }
        if (criteria.adults() < 1) {
            throw new InvalidSearchRequestException("INVALID_PARAMETER", "adults must be at least 1");
        }
        if (criteria.children() < 0) {
            throw new InvalidSearchRequestException("INVALID_PARAMETER", "children must be at least 0");
        }
    }
}
