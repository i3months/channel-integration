package com.stayhub.application;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stayhub.domain.SupplierCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class StartupCatalogSyncTest {

    private final FakeSupplierAdapter supplierA = new FakeSupplierAdapter(SupplierCode.A);
    private final FakeSupplierAdapter supplierB = new FakeSupplierAdapter(SupplierCode.B);
    private final SupplierRegistry registry = new SupplierRegistry(List.of(supplierB, supplierA));

    @Test
    void T44_sync_on_startup_이_false_면_어댑터를_호출하지_않는다() throws Exception {
        SyncSupplierCatalogService service = mock(SyncSupplierCatalogService.class);

        new StartupCatalogSync(registry, service, false).run(null);

        verifyNoInteractions(service);
    }

    @Test
    void SY04_켜져_있으면_공급사_코드_순서로_순차_동기화한다() throws Exception {
        SyncSupplierCatalogService service = mock(SyncSupplierCatalogService.class);

        new StartupCatalogSync(registry, service, true).run(null);

        var order = inOrder(service);
        order.verify(service).sync(SupplierCode.A);
        order.verify(service).sync(SupplierCode.B);
    }

    @Test
    void SY04_한_공급사에서_예외가_나도_다음_공급사로_넘어가고_예외를_던지지_않는다() throws Exception {
        SyncSupplierCatalogService service = mock(SyncSupplierCatalogService.class);
        when(service.sync(SupplierCode.A)).thenThrow(new IllegalStateException("db down"));

        new StartupCatalogSync(registry, service, true).run(null);

        verify(service).sync(SupplierCode.B);
    }
}
