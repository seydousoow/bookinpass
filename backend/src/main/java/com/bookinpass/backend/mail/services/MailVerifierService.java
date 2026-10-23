package com.bookinpass.backend.mail.services;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import java.util.Locale;
import java.util.Set;

/**
 * Adresse à laquelle envoyer : syntaxe valide, domaine non jetable, et domaine qui reçoit du courrier (MX).
 * Seule une réponse DNS négative écarte une adresse : un résolveur injoignable ou en erreur ne fait jamais perdre un
 * e-mail de réservation (SES signalera un éventuel rebond).
 */
@Slf4j
@Service
public class MailVerifierService {

    private static final Set<String> BLACKLISTED_DOMAINS = Set.of(
            "mailinator.com", "trashmail.com", "tempmail.com", "yopmail.com", "10minutemail.com", "guerrillamail.com",
            "getnada.com", "maildrop.cc", "dispostable.com", "fakeinbox.com", "temp-mail.org", "mytemp.email", "spambox.xyz");

    private final EmailValidator validator = EmailValidator.getInstance(true, true);

    public boolean isValid(String email) {
        if (email == null || email.isBlank() || !validator.isValid(email)) {
            return false;
        }
        String domain = email.substring(email.indexOf('@') + 1).toLowerCase(Locale.ROOT);
        return !BLACKLISTED_DOMAINS.contains(domain) && hasMXRecords(domain);
    }

    boolean hasMXRecords(String domain) {
        try {
            Lookup lookup = new Lookup(domain, Type.MX);
            Record[] records = lookup.run();
            return deliverable(domain, lookup.getResult(), records, lookup.getErrorString());
        } catch (Exception e) {
            log.warn("MX lookup of {} failed ({}): sending anyway", domain, e.toString());
            return true;
        }
    }

    /** Domaine ou MX inexistant : non ; MX trouvé : oui ; résolution impossible ({@code TRY_AGAIN}, {@code UNRECOVERABLE}) : oui. */
    static boolean deliverable(String domain, int result, Record[] records, String error) {
        return switch (result) {
            case Lookup.SUCCESSFUL -> records != null && records.length > 0;
            case Lookup.HOST_NOT_FOUND, Lookup.TYPE_NOT_FOUND -> false;
            default -> {
                log.warn("MX lookup of {} inconclusive ({}): sending anyway", domain, error);
                yield true;
            }
        };
    }
}
