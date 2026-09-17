package com.stayhub.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stayhub.application.SearchFailure;
import com.stayhub.application.SearchItem;
import com.stayhub.application.SearchResult;
import com.stayhub.application.SearchStaysService;
import com.stayhub.domain.DailyRate;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.Price;
import com.stayhub.domain.StaySearchCriteria;
import com.stayhub.domain.SupplierCode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    private static final String URL = "/api/v1/stays/search";
    private static final StaySearchCriteria SEP_1_TO_4 =
            new StaySearchCriteria(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 4), 2, 0);

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SearchStaysService service;

    private static SearchResult mockResult() {
        var a = new SearchItem(1, "Riverside Hotel Seoul", 1, "Deluxe Twin", 2, 1, SupplierCode.A,
                new Price(429_000L, "KRW", false, 3, List.of(
                        new DailyRate(LocalDate.of(2026, 9, 1), 120_000L, 12_000L),
                        new DailyRate(LocalDate.of(2026, 9, 2), 150_000L, 15_000L),
                        new DailyRate(LocalDate.of(2026, 9, 3), 120_000L, 12_000L))));
        var b = new SearchItem(3, "Riverside Hotel Seoul", 3, "Deluxe Twin Room", 2, 1, SupplierCode.B,
                new Price(452_000L, "KRW", true, 3, null));
        return new SearchResult(SEP_1_TO_4, List.of(a, b), List.of());
    }

    @Test
    void T70_정상_요청이면_200_이고_API03_구조이며_daily_null_이_필드로_있다() throws Exception {
        when(service.search(SEP_1_TO_4)).thenReturn(mockResult());

        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("adults", "2").param("children", "0"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "checkIn": "2026-09-01",
                          "checkOut": "2026-09-04",
                          "nights": 3,
                          "adults": 2,
                          "children": 0,
                          "items": [
                            {
                              "stayId": 1,
                              "stayName": "Riverside Hotel Seoul",
                              "roomTypeId": 1,
                              "roomTypeName": "Deluxe Twin",
                              "maxOccupancy": 2,
                              "availableRooms": 1,
                              "supplier": "A",
                              "price": {
                                "totalAmount": 429000,
                                "currency": "KRW",
                                "breakfastIncluded": false,
                                "nights": 3,
                                "daily": [
                                  { "date": "2026-09-01", "nightlyRate": 120000, "taxAmount": 12000 },
                                  { "date": "2026-09-02", "nightlyRate": 150000, "taxAmount": 15000 },
                                  { "date": "2026-09-03", "nightlyRate": 120000, "taxAmount": 12000 }
                                ]
                              }
                            },
                            {
                              "stayId": 3,
                              "stayName": "Riverside Hotel Seoul",
                              "roomTypeId": 3,
                              "roomTypeName": "Deluxe Twin Room",
                              "maxOccupancy": 2,
                              "availableRooms": 1,
                              "supplier": "B",
                              "price": {
                                "totalAmount": 452000,
                                "currency": "KRW",
                                "breakfastIncluded": true,
                                "nights": 3,
                                "daily": null
                              }
                            }
                          ],
                          "failures": []
                        }
                        """, JsonCompareMode.STRICT))
                .andExpect(jsonPath("$.items[1].price.daily").isEmpty())
                .andExpect(jsonPath("$.items[1].price", org.hamcrest.Matchers.hasKey("daily")));
    }

    @Test
    void API03_부분_실패는_failures_로_드러나고_여전히_200() throws Exception {
        when(service.search(SEP_1_TO_4)).thenReturn(new SearchResult(SEP_1_TO_4, List.of(), List.of(
                new SearchFailure(SupplierCode.A, FailureReason.UNAVAILABLE, "1/1 chunks failed: 503"),
                new SearchFailure(SupplierCode.B, FailureReason.TIMEOUT, "1/1 chunks failed: search budget exceeded"))));

        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04").param("adults", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"items":[],"failures":[
                          {"supplier":"A","reason":"UNAVAILABLE","message":"1/1 chunks failed: 503"},
                          {"supplier":"B","reason":"TIMEOUT","message":"1/1 chunks failed: search budget exceeded"}]}
                        """));
    }

    @Test
    void T71_checkOut_이_없으면_400_MISSING_PARAMETER() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"MISSING_PARAMETER","message":"checkOut is required"}""", JsonCompareMode.STRICT));
        verify(service, never()).search(any());
    }

    @Test
    void T72_날짜_형식이_틀리면_400_INVALID_PARAMETER() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-13-01").param("checkOut", "2026-09-04").param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_PARAMETER","message":"checkIn must be yyyy-MM-dd"}""", JsonCompareMode.STRICT));
    }

    @Test
    void API02_인원이_정수가_아니면_400_INVALID_PARAMETER() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04").param("adults", "two"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_PARAMETER","message":"adults must be an integer"}""", JsonCompareMode.STRICT));
    }

    @Test
    void T73_checkOut_이_checkIn_과_같으면_400_INVALID_DATE_RANGE() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-01").param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_DATE_RANGE","message":"checkOut must be after checkIn"}""", JsonCompareMode.STRICT));
        verify(service, never()).search(any());
    }

    @Test
    void API02_checkOut_이_checkIn_보다_이전이어도_400_INVALID_DATE_RANGE() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-04").param("checkOut", "2026-09-01").param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DATE_RANGE"));
    }

    @Test
    void T74_adults_가_0_이면_400_INVALID_PARAMETER() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04").param("adults", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_PARAMETER","message":"adults must be at least 1"}""", JsonCompareMode.STRICT));
    }

    @Test
    void API02_children_이_음수면_400_INVALID_PARAMETER() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("adults", "2").param("children", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_PARAMETER","message":"children must be at least 0"}""", JsonCompareMode.STRICT));
    }

    @Test
    void T75_children_을_생략하면_서비스에_0_으로_전달한다() throws Exception {
        when(service.search(any())).thenReturn(new SearchResult(SEP_1_TO_4, List.of(), List.of()));

        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04").param("adults", "2"))
                .andExpect(status().isOk());

        var captor = ArgumentCaptor.forClass(StaySearchCriteria.class);
        verify(service).search(captor.capture());
        assertThat(captor.getValue()).isEqualTo(SEP_1_TO_4);
    }

    @Test
    void T76_31박이면_400_INVALID_DATE_RANGE() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-10-02").param("adults", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_DATE_RANGE","message":"stay length must be 30 nights or less"}""", JsonCompareMode.STRICT));
    }

    @Test
    void API02_30박은_허용한다() throws Exception {
        when(service.search(any())).thenReturn(new SearchResult(SEP_1_TO_4, List.of(), List.of()));

        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-10-01").param("adults", "2"))
                .andExpect(status().isOk());
    }

    @Test
    void API05_예상하지_못한_예외는_500_INTERNAL_ERROR_이고_내용을_노출하지_않는다() throws Exception {
        when(service.search(any())).thenThrow(new IllegalStateException("database password is wrong"));

        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04").param("adults", "2"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().json("""
                        {"code":"INTERNAL_ERROR","message":"unexpected error"}""", JsonCompareMode.STRICT));
    }

    @Test
    void API05_스프링_표준_오류는_500_으로_바꾸지_않고_원래_상태를_유지한다() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(URL)
                        .param("checkIn", "2026-09-01").param("checkOut", "2026-09-04").param("adults", "2"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void T110_adults_가_21_이면_400_INVALID_PARAMETER() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04").param("adults", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_PARAMETER","message":"adults must be at most 20"}""", JsonCompareMode.STRICT));
    }

    @Test
    void T111_인원_합계가_넘칠_만큼_크면_400_이고_서비스를_호출하지_않는다() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("adults", "2147483647").param("children", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));
        verify(service, never()).search(any());
    }

    @Test
    void FX05_children_이_21_이면_400_INVALID_PARAMETER() throws Exception {
        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("adults", "2").param("children", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {"code":"INVALID_PARAMETER","message":"children must be at most 20"}""", JsonCompareMode.STRICT));
    }

    @Test
    void T112_adults_20_children_20_은_허용한다() throws Exception {
        when(service.search(any())).thenReturn(new SearchResult(SEP_1_TO_4, List.of(), List.of()));

        mvc.perform(get(URL).param("checkIn", "2026-09-01").param("checkOut", "2026-09-04")
                        .param("adults", "20").param("children", "20"))
                .andExpect(status().isOk());
    }
}
