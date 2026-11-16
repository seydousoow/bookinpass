package com.bookinpass.backend.dto.response;

import com.bookinpass.backend.config.ids.PublicId;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Incident signalé ; {@code resolved_at} vide tant qu'il est ouvert.
 */
public record StaffIncidentResponse(@PublicId Integer id,
                                    @JsonProperty("route_code") String routeCode,
                                    String origin,
                                    String destination,
                                    @JsonProperty("departure_id") @PublicId Integer departureId,
                                    @JsonProperty("departs_at") String departsAt,
                                    String type,
                                    String description,
                                    @JsonProperty("reported_by") String reportedBy,
                                    @JsonProperty("reported_at") String reportedAt,
                                    @JsonProperty("resolved_at") String resolvedAt,
                                    String resolution) {
}
