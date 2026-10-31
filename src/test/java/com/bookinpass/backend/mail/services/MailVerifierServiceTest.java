package com.bookinpass.backend.mail.services;

import org.junit.jupiter.api.Test;
import org.xbill.DNS.Lookup;

import static org.assertj.core.api.Assertions.assertThat;

class MailVerifierServiceTest {

    /**
     * Sans requête DNS : seuls les domaines listés ont un MX.
     */
    private final MailVerifierService verifier = new MailVerifierService() {
        @Override
        boolean hasMXRecords(String domain) {
            return domain.equals("example.com") || domain.equals("yopmail.com");
        }
    };

    @Test
    void acceptsOnlyWellFormedDeliverableNonDisposableAddresses() {
        assertThat(verifier.isValid("amina@example.com")).isTrue();
        assertThat(verifier.isValid("not-an-email")).isFalse();
        assertThat(verifier.isValid("amina@yopmail.com")).isFalse();
        assertThat(verifier.isValid("amina@no-mx.test")).isFalse();
        assertThat(verifier.isValid(null)).isFalse();
    }

    @Test
    void onlyANegativeDnsAnswerRejectsADomain() {
        assertThat(MailVerifierService.deliverable("example.com", Lookup.HOST_NOT_FOUND, null, "host not found")).isFalse();
        assertThat(MailVerifierService.deliverable("example.com", Lookup.TYPE_NOT_FOUND, null, "type not found")).isFalse();
        // Résolveur injoignable (image native, réseau) : l'e-mail de réservation part quand même.
        assertThat(MailVerifierService.deliverable("example.com", Lookup.TRY_AGAIN, null, "timed out")).isTrue();
        assertThat(MailVerifierService.deliverable("example.com", Lookup.UNRECOVERABLE, null, "SERVFAIL")).isTrue();
    }
}
