package com.dcpost.customer.integration.config;

import com.dcpost.customer.integration.pricing.*;
import com.dcpost.customer.integration.shipping.*;
import com.dcpost.customer.integration.warehouse.*;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IntegrationProperties.class)
public class IntegrationConfiguration {
    @Bean
    PricingClient pricingClient(IntegrationProperties properties, Clock clock) {
        return new PricingHttpAdapter(properties.pricingBaseUrl(), properties.connectTimeout(), properties.readTimeout(), clock);
    }

    @Bean
    WarehouseClient warehouseClient(IntegrationProperties properties) {
        return new WarehouseHttpAdapter(properties.warehouseBaseUrl(), properties.connectTimeout(), properties.readTimeout());
    }

    @Bean
    ShippingClient shippingClient(IntegrationProperties properties) {
        return new ShippingHttpAdapter(properties.shippingBaseUrl(), properties.connectTimeout(), properties.readTimeout());
    }
}
