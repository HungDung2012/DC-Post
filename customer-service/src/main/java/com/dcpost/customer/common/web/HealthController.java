package com.dcpost.customer.common.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/public/health")
    public Map<String, String> health() { return Map.of("status", "UP"); }
}
