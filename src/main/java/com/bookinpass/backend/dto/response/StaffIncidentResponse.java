package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Incident signalé ; {@code resolved_at} vide tant qu'il est ouvert.
 */
public record StaffIncidentResponse(Integer id,
                                    @JsonProperty("route_code") String routeCode,
                                    String origin,
                                    String destination,
                                    @JsonProperty("departure_id") Integer departureId,
                                    @JsonProperty("departs_at") String departsAt,
                                    String type,
                                    String description,
                                    @JsonProperty("reported_by") String reportedBy,
                                    @JsonProperty("reported_at") String reportedAt,
                                    @JsonProperty("resolved_at") String resolvedAt,
                                    String resolution) {
}
