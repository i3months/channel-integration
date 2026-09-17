package com.stayhub.adapter.in.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stayhub.application.SyncResult;
import com.stayhub.application.SyncSupplierCatalogService;
import com.stayhub.application.UnknownSupplierException;
import com.stayhub.domain.FailureReason;
import com.stayhub.domain.SupplierCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SupplierSyncController.class)
class SupplierSyncControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    SyncSupplierCatalogService service;

    @Test
    void T77_동기화_성공이면_200_과_건수() throws Exception {
        when(service.sync(SupplierCode.A)).thenReturn(new SyncResult(SupplierCode.A, 2, 0, 0, null, null));

        mvc.perform(post("/internal/suppliers/a/sync"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"supplier":"A","added":2,"updated":0,"deactivated":0}""", JsonCompareMode.STRICT));
    }

    @Test
    void T78_알_수_없는_공급사면_404_UNKNOWN_SUPPLIER() throws Exception {
        mvc.perform(post("/internal/suppliers/x/sync"))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {"code":"UNKNOWN_SUPPLIER","message":"unknown supplier: x"}""", JsonCompareMode.STRICT));
    }

    @Test
    void T108_코드는_있지만_어댑터가_없는_공급사도_404() throws Exception {
        when(service.sync(SupplierCode.B)).thenThrow(new UnknownSupplierException(SupplierCode.B));

        mvc.perform(post("/internal/suppliers/B/sync"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNKNOWN_SUPPLIER"));
    }

    @Test
    void T79_공급사_호출_실패면_502_와_SUPPLIER_사유_코드() throws Exception {
        when(service.sync(SupplierCode.A)).thenReturn(
                new SyncResult(SupplierCode.A, 0, 0, 0, FailureReason.UNAVAILABLE, "503 SERVICE_UNAVAILABLE"));

        mvc.perform(post("/internal/suppliers/A/sync"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {"code":"SUPPLIER_UNAVAILABLE","message":"503 SERVICE_UNAVAILABLE"}""", JsonCompareMode.STRICT));
    }

    @Test
    void SY05_반영_중_예외면_500_INTERNAL_ERROR() throws Exception {
        when(service.sync(SupplierCode.A)).thenThrow(new IllegalStateException("constraint violation"));

        mvc.perform(post("/internal/suppliers/a/sync"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }

    @Test
    void T109_반영_중_난_IllegalArgumentException_은_404_가_아니라_500() throws Exception {
        when(service.sync(SupplierCode.A)).thenThrow(new IllegalArgumentException("unknown stay id: 9"));

        mvc.perform(post("/internal/suppliers/a/sync"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
    }
}
