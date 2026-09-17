package com.stayhub.application;

import com.stayhub.domain.port.SupplierAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * 기동 시 공급사별로 숙소 목록을 한 번 동기화한다. 순차 실행이며, 실패해도 앱 기동을 막지 않는다.
 */
public class StartupCatalogSync implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCatalogSync.class);

    private final SupplierRegistry registry;
    private final SyncSupplierCatalogService service;
    private final boolean enabled;

    public StartupCatalogSync(SupplierRegistry registry, SyncSupplierCatalogService service, boolean enabled) {
        this.registry = registry;
        this.service = service;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("event=catalog_sync_skipped reason=sync-on-startup-disabled");
            return;
        }
        for (SupplierAdapter adapter : registry.all()) {
            try {
                SyncResult result = service.sync(adapter.supplierCode());
                log.info("event=catalog_sync supplier={} success={} added={} updated={} deactivated={} reason={} message=\"{}\"",
                        result.supplier(), result.isSuccess(), result.added(), result.updated(), result.deactivated(),
                        result.failureReason(), result.failureMessage());
            } catch (RuntimeException e) {
                log.error("event=catalog_sync_error supplier={}", adapter.supplierCode(), e);
            }
        }
    }
}
