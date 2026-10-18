package com.bookinpass.backend.mail.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailVerifierServiceTest {

    /** Sans requête DNS : seuls les domaines listés ont un MX. */
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
}
