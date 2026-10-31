package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Réservation d'activité vue par le client (bon d'échange, annulation, avis).
 *
 * @param status            {@code held}, {@code confirmed}, {@code released}, {@code cancelled}
 * @param cancellableUntil  fin de l'annulation gratuite ; nulle si l'activité n'est pas remboursable
 * @param cancellable       annulable maintenant par le client, avec remboursement total
 * @param refunded          montant déjà remboursé (ou en cours) ; « 0 » sinon
 * @param canReview         le créneau est passé, la réservation payée et pas encore notée
 */
public record ActivityBookingResponse(String reference, String status, Activity activity,
                                      @JsonProperty("option_title") String optionTitle, @JsonProperty("starts_at") String startsAt,
                                      int adults, int children, @JsonProperty("adult_price") String adultPrice,
                                      @JsonProperty("child_price") String childPrice, String total, String currency,
                                      @JsonProperty("given_name") String givenName, @JsonProperty("family_name") String familyName,
                                      String email, @JsonProperty("cancellable_until") String cancellableUntil, boolean cancellable,
                                      String refunded, @JsonProperty("cancel_reason") String cancelReason,
                                      @JsonProperty("can_review") boolean canReview, boolean reviewed) {

    public record Activity(String slug, String title, ActivityImage image, ActivityCityResponse city,
                           @JsonProperty("meeting_point") String meetingPoint, @JsonProperty("provider_name") String providerName) {
    }
}
