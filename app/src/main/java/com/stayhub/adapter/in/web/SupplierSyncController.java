package com.stayhub.adapter.in.web;

import com.stayhub.application.SyncResult;
import com.stayhub.application.SyncSupplierCatalogService;
import com.stayhub.application.UnknownSupplierException;
import com.stayhub.domain.SupplierCode;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SupplierSyncController {

    private static final Logger log = LoggerFactory.getLogger(SupplierSyncController.class);

    private final SyncSupplierCatalogService service;

    public SupplierSyncController(SyncSupplierCatalogService service) {
        this.service = service;
    }

    @Operation(summary = "공급사 숙소 목록을 다시 받아 내부 식별자 매핑을 갱신한다")
    @PostMapping("/internal/suppliers/{supplierCode}/sync")
    public ResponseEntity<?> sync(@PathVariable String supplierCode) {
        SupplierCode supplier;
        try {
            supplier = SupplierCode.fromString(supplierCode);
        } catch (IllegalArgumentException e) {
            return unknownSupplier(supplierCode);
        }

        SyncResult result;
        try {
            result = service.sync(supplier);
        } catch (UnknownSupplierException e) {
            return unknownSupplier(supplierCode);
        } catch (RuntimeException e) {
            log.error("event=catalog_sync_error supplier={}", supplier, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiError("INTERNAL_ERROR", "catalog sync failed"));
        }

        if (!result.isSuccess()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ApiError("SUPPLIER_" + result.failureReason().name(), result.failureMessage()));
        }
        return ResponseEntity.ok(new SyncResponse(result.supplier().name(), result.added(), result.updated(),
                result.deactivated()));
    }

    private static ResponseEntity<ApiError> unknownSupplier(String supplierCode) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("UNKNOWN_SUPPLIER", "unknown supplier: " + supplierCode));
    }

    record SyncResponse(String supplier, int added, int updated, int deactivated) {
    }
}
