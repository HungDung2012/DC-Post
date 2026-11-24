package com.dcpost.customer.integration.warehouse;
import com.dcpost.customer.integration.warehouse.dto.DropOffLocation;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import org.springframework.http.HttpMethod;
import com.dcpost.customer.integration.common.DevelopmentHttpTransport;
import com.dcpost.customer.integration.common.IntegrationException;
public class WarehouseHttpAdapter implements WarehouseClient {
    private final DevelopmentHttpTransport transport;

    public WarehouseHttpAdapter(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        transport = new DevelopmentHttpTransport(baseUrl, connectTimeout, readTimeout);
    }

    public List<DropOffLocation> getDropOffLocations() {
        var response = transport.exchange(HttpMethod.GET, "/dev/warehouse/locations", null, null, false);
        var locations = response.get("locations");
        if (locations == null || !locations.isArray())
            throw new IntegrationException(IntegrationException.Kind.INVALID_RESPONSE, false);
        var result = new ArrayList<DropOffLocation>();
        for (var location : locations) {
            result.add(new DropOffLocation(DevelopmentHttpTransport.requiredText(location, "id", false),
                    DevelopmentHttpTransport.requiredText(location, "name", false),
                    DevelopmentHttpTransport.requiredText(location, "address", false)));
        }
        return List.copyOf(result);
    }
}
