package com.dcpost.customer.integration.shipping;
import com.dcpost.customer.integration.shipping.dto.*;
public interface ShippingClient {
    SubmissionResult submit(ShipmentSubmission submission, String requestKey);
    SubmissionLookup findSubmission(String trackingCode);
    CancellationResult cancel(String trackingCode, String requestKey, String reason);
    ShipmentTracking getTracking(String trackingCode);
}
