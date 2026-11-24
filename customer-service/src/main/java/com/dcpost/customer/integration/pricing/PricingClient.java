package com.dcpost.customer.integration.pricing;

import com.dcpost.customer.integration.pricing.dto.FeeRequest;
import com.dcpost.customer.integration.pricing.dto.FeeQuote;

public interface PricingClient {
    FeeQuote quote(FeeRequest request);
}
