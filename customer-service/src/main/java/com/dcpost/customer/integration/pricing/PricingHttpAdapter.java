package com.dcpost.customer.integration.pricing;

import com.dcpost.customer.integration.pricing.dto.*;
import java.time.Clock;
import java.time.Duration;
import com.dcpost.customer.integration.common.DevelopmentHttpTransport;
import com.dcpost.customer.integration.common.IntegrationException;
import org.springframework.http.HttpMethod;

public class PricingHttpAdapter implements PricingClient {
    private final DevelopmentHttpTransport transport;
    private final Clock clock;

    public PricingHttpAdapter(String baseUrl, Duration connectTimeout, Duration readTimeout, Clock clock) {
        this.transport = new DevelopmentHttpTransport(baseUrl, connectTimeout, readTimeout);
        this.clock = clock;
    }

    @Override
    public FeeQuote quote(FeeRequest request) {
        var result = transport.exchange(HttpMethod.POST, "/dev/pricing/quote", request, null, false);
        var amount = result.get("amount");
        if (amount == null || !amount.isNumber() || amount.decimalValue().signum() < 0
                || amount.decimalValue().stripTrailingZeros().scale() > 0
                || !"VND".equals(DevelopmentHttpTransport.requiredText(result, "currency", false)))
            throw new IntegrationException(IntegrationException.Kind.INVALID_RESPONSE, false);
        return new FeeQuote(amount.decimalValue(), "VND", clock.instant());
    }
}
