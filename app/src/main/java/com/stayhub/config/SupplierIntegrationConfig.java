package com.stayhub.config;

import com.stayhub.adapter.out.supplier.common.IntegrationProperties;
import com.stayhub.adapter.out.supplier.common.SupplierCallExecutor;
import com.stayhub.adapter.out.supplier.common.SupplierProperties;
import com.stayhub.adapter.out.supplier.common.WebClientFactory;
import com.stayhub.application.SupplierRegistry;
import com.stayhub.domain.port.SupplierAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SupplierIntegrationConfig {

    @Bean
    public WebClientFactory webClientFactory(SupplierProperties suppliers, IntegrationProperties integration) {
        return new WebClientFactory(suppliers, integration.connectTimeout());
    }

    @Bean
    public SupplierCallExecutor supplierCallExecutor(IntegrationProperties integration) {
        return new SupplierCallExecutor(integration.retry());
    }

    /** 어댑터가 아직 하나도 없어도 뜨도록 ObjectProvider 로 받는다. */
    @Bean
    public SupplierRegistry supplierRegistry(ObjectProvider<SupplierAdapter> adapters) {
        return new SupplierRegistry(adapters.orderedStream().toList());
    }
}
