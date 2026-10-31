package com.bookinpass.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Suite d'un paiement PayDunya : {@code redirect} (ouvrir {@code url} : Wave, page carte PayDunya), {@code qr}
 * (QR code Orange Money à {@code url}, ou applications Orange Money / Max it), {@code otp} (code accepté, paiement
 * relu), {@code phone} (valider sur le téléphone). Le front relit ensuite {@code …/mobile-payment/confirmation}.
 *
 * @param status statut de la réservation à la réponse ({@code held}, ou {@code confirmed} si le paiement est déjà là)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MobilePaymentResponse(String step,
                                    String url,
                                    @JsonProperty("om_url") String omUrl,
                                    @JsonProperty("maxit_url") String maxitUrl,
                                    String status) {
}
