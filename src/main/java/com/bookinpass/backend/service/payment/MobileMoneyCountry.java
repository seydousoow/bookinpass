package com.bookinpass.backend.service.payment;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Pays du mobile money : indicatif imposé et numéro national d'un mobile. Le client ne saisit que le numéro national ;
 * un indicatif collé ({@code +221…}, {@code 00221…}) est retiré s'il est celui du pays.
 */
public enum MobileMoneyCountry {

    /**
     * 9 chiffres : 70 Expresso, 75 Promobile, 76 Free, 77 et 78 Orange.
     */
    SENEGAL("SN", "221", "7[05678]\\d{7}"),
    /**
     * 10 chiffres depuis 2021 : 01 Moov, 05 MTN, 07 Orange.
     */
    COTE_D_IVOIRE("CI", "225", "0[157]\\d{8}"),
    /**
     * 8 chiffres, mobiles en 5, 6 et 7.
     */
    BURKINA_FASO("BF", "226", "[567]\\d{7}"),
    /**
     * 8 chiffres, mobiles de 5 à 9.
     */
    MALI("ML", "223", "[5-9]\\d{7}");

    private static final Pattern SEPARATORS = Pattern.compile("[\\s.\\-()]");

    private final String code;
    private final String dialCode;
    private final Pattern mobile;

    MobileMoneyCountry(String code, String dialCode, String mobile) {
        this.code = code;
        this.dialCode = dialCode;
        this.mobile = Pattern.compile(mobile);
    }

    /**
     * Code ISO 3166-1 alpha-2.
     */
    public String code() {
        return code;
    }

    /**
     * Indicatif sans {@code +}.
     */
    public String dialCode() {
        return dialCode;
    }

    /**
     * Numéro national d'un mobile du pays, nettoyé ; vide s'il n'en est pas un.
     */
    Optional<String> national(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String digits = SEPARATORS.matcher(input).replaceAll("");
        if (digits.startsWith("+" + dialCode)) {
            digits = digits.substring(dialCode.length() + 1);
        } else if (digits.startsWith("00" + dialCode)) {
            digits = digits.substring(dialCode.length() + 2);
        }
        return mobile.matcher(digits).matches() ? Optional.of(digits) : Optional.empty();
    }
}
