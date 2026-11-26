package com.dcpost.customer.integration.shipping;
import com.dcpost.customer.integration.shipping.dto.*;
public interface ShippingClient {
    /** True only after verifying authoritative NOT_FOUND and receiver deduplication of the stable key, including late in-flight requests. */
    default boolean supportsSafeResubmission() { return false; }
    SubmissionResult submit(ShipmentSubmission submission, String requestKey);
    SubmissionLookup findSubmission(String trackingCode);
    CancellationResult cancel(String trackingCode, String requestKey, String reason);
    ShipmentTracking getTracking(String trackingCode);
}
