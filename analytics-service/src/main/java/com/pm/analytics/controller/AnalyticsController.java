package com.pm.analytics.controller;

import com.pm.analytics.dto.AnalyticsSummaryDTO;
import com.pm.analytics.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read-only analytics API. Exposed to clients through the gateway route
 * {@code /api/analytics/**} (StripPrefix=1 -> {@code /analytics/**}), protected by JWT.
 *
 * <p>Note: this query API is an extension beyond a pure Kafka-consumer analytics service; it
 * lets clients read aggregates without touching the patient store.
 */
@RestController
@RequestMapping("/analytics")
@Tag(name = "Analytics", description = "Read-only analytics aggregates")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Get an analytics summary (total events, patients created)")
    public ResponseEntity<AnalyticsSummaryDTO> summary() {
        return ResponseEntity.ok(analyticsService.summary());
    }

    @GetMapping("/patients/count")
    @Operation(summary = "Get the number of patients created")
    public ResponseEntity<Map<String, Long>> patientsCreated() {
        return ResponseEntity.ok(Map.of("patientsCreated", analyticsService.patientsCreatedCount()));
    }
}
