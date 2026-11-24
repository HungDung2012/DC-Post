package com.dcpost.customer.integration.shipping;
import com.dcpost.customer.integration.shipping.dto.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.web.util.UriUtils;
import com.dcpost.customer.integration.common.DevelopmentHttpTransport;
import com.dcpost.customer.integration.common.IntegrationException;
import com.fasterxml.jackson.databind.JsonNode;
import static com.dcpost.customer.integration.common.DevelopmentHttpTransport.requiredText;
import static com.dcpost.customer.integration.common.IntegrationException.Kind.INVALID_RESPONSE;

public class ShippingHttpAdapter implements ShippingClient {
    private final DevelopmentHttpTransport transport;

    public ShippingHttpAdapter(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        transport = new DevelopmentHttpTransport(baseUrl, connectTimeout, readTimeout);
    }

    public SubmissionResult submit(ShipmentSubmission submission, String requestKey) {
        var response = transport.exchange(HttpMethod.POST, "/dev/shipping/submissions", submission, requestKey, true);
        return new SubmissionResult(outcome(response));
    }

    public SubmissionLookup findSubmission(String trackingCode) {
        var response = transport.exchange(HttpMethod.GET, "/dev/shipping/submissions/" + segment(trackingCode), null, null, false);
        try {
            return new SubmissionLookup(SubmissionLookup.Status.valueOf(requiredText(response, "status", false)));
        } catch (IllegalArgumentException error) {
            throw new IntegrationException(INVALID_RESPONSE, false);
        }
    }

    public CancellationResult cancel(String trackingCode, String requestKey, String reason) {
        var response = transport.exchange(HttpMethod.POST, "/dev/shipping/submissions/" + segment(trackingCode) + "/cancel",
                Map.of("reason", reason), requestKey, true);
        return new CancellationResult(outcome(response));
    }

    public ShipmentTracking getTracking(String trackingCode) {
        var response = transport.exchange(HttpMethod.GET, "/dev/shipping/tracking/" + segment(trackingCode), null, null, false);
        try {
            String receivedCode = requiredText(response, "trackingCode", false);
            if (!trackingCode.equals(receivedCode)) throw new IntegrationException(INVALID_RESPONSE, false);
            var events = response.get("publicEvents");
            if (events == null || !events.isArray()) throw new IntegrationException(INVALID_RESPONSE, false);
            var publicEvents = new ArrayList<ShipmentTracking.PublicEvent>();
            for (var event : events) {
                publicEvents.add(new ShipmentTracking.PublicEvent(requiredText(event, "sourceStatus", false),
                        Instant.parse(requiredText(event, "occurredAt", false)), requiredText(event, "area", false)));
            }
            var details = response.get("privateDetails");
            var privateDetails = details == null || details.isNull() ? null : new ShipmentTracking.PrivateDetails(
                    requiredText(details, "recipientName", false), requiredText(details, "recipientPhone", false),
                    requiredText(details, "recipientAddress", false));
            return new ShipmentTracking(receivedCode, requiredText(response, "sourceStatus", false),
                    Instant.parse(requiredText(response, "updatedAt", false)), java.util.List.copyOf(publicEvents), privateDetails);
        } catch (java.time.format.DateTimeParseException error) {
            throw new IntegrationException(INVALID_RESPONSE, false);
        }
    }

    private static OperationOutcome outcome(JsonNode response) {
        try {
            return OperationOutcome.valueOf(requiredText(response, "outcome", true));
        } catch (IllegalArgumentException error) {
            throw new IntegrationException(INVALID_RESPONSE, true);
        }
    }

    private static String segment(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
