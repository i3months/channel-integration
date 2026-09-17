package com.stayhub.config;

import com.stayhub.adapter.out.supplier.common.IntegrationProperties;
import com.stayhub.adapter.out.supplier.common.SupplierProperties;
import com.stayhub.domain.SupplierCode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableConfigurationProperties(IntegrationProperties.class)
public class PropertiesConfig {

    @Bean
    public SupplierProperties supplierProperties(Environment environment) {
        var entries = Binder.get(environment)
                .bind("suppliers", Bindable.mapOf(SupplierCode.class, SupplierProperties.Entry.class))
                .orElseThrow(() -> new IllegalStateException("suppliers configuration is missing"));
        return new SupplierProperties(entries);
    }
}
