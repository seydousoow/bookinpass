package com.bookinpass.backend.service.payment;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Moyens de paiement PayDunya proposés sur notre propre page (SOFTPAY) : le client choisit le pays et le moyen, saisit
 * son numéro, et le backend transmet le paiement à PayDunya. La carte PayDunya fait exception : la saisie d'un numéro
 * de carte exige une certification PCI-DSS, elle se fait donc sur la page PayDunya (facture limitée au canal
 * {@code card}). Elle complète la carte Stripe, qui accepte American Express.
 */
public enum PaymentMethod {

    PAYDUNYA_CARD("paydunya-card", null, null, Step.REDIRECT, false, null),
    /**
     * Lien Wave à ouvrir (application Wave ou page de paiement).
     */
    WAVE_SENEGAL("wave-senegal", MobileMoneyCountry.SENEGAL, "wave-senegal", Step.REDIRECT, false, null),
    /**
     * QR code et liens vers les applications Orange Money et Max it.
     */
    ORANGE_MONEY_SENEGAL("orange-money-senegal", MobileMoneyCountry.SENEGAL, "new-orange-money-senegal", Step.QR, false, "7[78]\\d{7}"),
    WAVE_COTE_D_IVOIRE("wave-ci", MobileMoneyCountry.COTE_D_IVOIRE, "wave-ci", Step.REDIRECT, false, null),
    /**
     * Code de paiement obtenu par le client au #144*82#, option 2, puis saisi sur notre page.
     */
    ORANGE_MONEY_COTE_D_IVOIRE("orange-money-ci", MobileMoneyCountry.COTE_D_IVOIRE, "orange-money-ci", Step.OTP, true, "07\\d{8}"),
    /**
     * Code OTP Orange Money saisi sur notre page.
     */
    ORANGE_MONEY_BURKINA_FASO("orange-money-burkina", MobileMoneyCountry.BURKINA_FASO, "orange-money-burkina", Step.OTP, true, null),
    /**
     * Paiement validé par le client sur son téléphone.
     */
    ORANGE_MONEY_MALI("orange-money-mali", MobileMoneyCountry.MALI, "orange-money-mali", Step.PHONE, false, null);

    private final String id;
    private final MobileMoneyCountry country;
    private final String softpayPath;
    private final Step step;
    private final boolean otp;
    private final Pattern operator;

    PaymentMethod(String id, MobileMoneyCountry country, String softpayPath, Step step, boolean otp, String operator) {
        this.id = id;
        this.country = country;
        this.softpayPath = softpayPath;
        this.step = step;
        this.otp = otp;
        this.operator = operator == null ? null : Pattern.compile(operator);
    }

    public static Optional<PaymentMethod> of(String id) {
        return Arrays.stream(values()).filter(m -> m.id.equals(id)).findFirst();
    }

    /**
     * Identifiant échangé avec le front et la configuration ({@code PAYDUNYA_METHODS}).
     */
    public String id() {
        return id;
    }

    /**
     * Pays du numéro ; nul pour la carte.
     */
    public MobileMoneyCountry country() {
        return country;
    }

    /**
     * Point d'entrée SOFTPAY ({@code /softpay/{path}}) ; nul pour la carte.
     */
    public String softpayPath() {
        return softpayPath;
    }

    public Step step() {
        return step;
    }

    /**
     * Code OTP à saisir avec le numéro.
     */
    public boolean otp() {
        return otp;
    }

    public boolean mobileMoney() {
        return country != null;
    }

    /**
     * Numéro national accepté pour ce moyen : mobile du pays, et de l'opérateur pour Orange Money au Sénégal et en
     * Côte d'Ivoire (Wave fonctionne avec toute puce). Vide s'il est refusé.
     */
    public Optional<String> national(String phone) {
        return country.national(phone).filter(n -> operator == null || operator.matcher(n).matches());
    }

    /**
     * Ce que fait le client après l'envoi du formulaire.
     */
    public enum Step {
        /**
         * Ouvrir {@code url}.
         */
        REDIRECT,
        /**
         * Scanner le QR code de {@code url}, ou ouvrir l'application Orange Money / Max it.
         */
        QR,
        /**
         * Rien : le code saisi a suffi, le paiement est relu aussitôt.
         */
        OTP,
        /**
         * Valider sur son téléphone, la page attend la confirmation.
         */
        PHONE
    }
}
