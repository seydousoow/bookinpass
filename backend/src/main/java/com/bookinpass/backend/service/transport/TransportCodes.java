package com.bookinpass.backend.service.transport;

import java.security.SecureRandom;

/** Codes lus à voix haute ou recopiés à la main (réservation, billet, bagage) : sans 0/O ni 1/I. */
public final class TransportCodes {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TransportCodes() {
    }

    public static String random(int length) {
        StringBuilder code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
