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

    /**
     * 성인, 아동 각각의 상한 (FX-05). 상한이 없으면 int 최댓값에서 투숙 인원 합계가 음수로 넘쳐 인원 필터를 통과한다.
     * 여러 객실을 묶어 파는 경우는 범위 밖이므로 객실 한 개의 수용 인원보다 넉넉하면 충분하다.
     */
    static final int MAX_GUESTS_PER_TYPE = 20;

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
        if (criteria.adults() > MAX_GUESTS_PER_TYPE) {
            throw new InvalidSearchRequestException("INVALID_PARAMETER",
                    "adults must be at most " + MAX_GUESTS_PER_TYPE);
        }
        if (criteria.children() < 0) {
            throw new InvalidSearchRequestException("INVALID_PARAMETER", "children must be at least 0");
        }
        if (criteria.children() > MAX_GUESTS_PER_TYPE) {
            throw new InvalidSearchRequestException("INVALID_PARAMETER",
                    "children must be at most " + MAX_GUESTS_PER_TYPE);
        }
    }
}
