package com.housingplatform.shared.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Root endpoint controller for API information and health checks */
@RestController
@Tag(name = "Root", description = "Root API endpoint")
public class RootController {

  @GetMapping("/")
  @Operation(summary = "API root", description = "Returns API information and available endpoints")
  public ResponseEntity<Map<String, Object>> root() {
    Map<String, Object> response = new HashMap<>();
    response.put("service", "Housing Platform API");
    response.put("version", "1.0.0");
    response.put("status", "UP");
    response.put(
        "endpoints",
        Map.of(
            "api", "/api/v1/",
            "health", "/actuator/health",
            "swagger", "/swagger-ui.html",
            "api-docs", "/api-docs"));
    return ResponseEntity.ok(response);
  }
}
