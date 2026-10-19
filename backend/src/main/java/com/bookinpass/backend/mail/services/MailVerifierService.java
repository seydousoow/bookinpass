package com.bookinpass.backend.mail.services;

import org.apache.commons.validator.routines.EmailValidator;
import org.springframework.stereotype.Service;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Type;

import java.util.Locale;
import java.util.Set;

/** Adresse à laquelle envoyer : syntaxe valide, domaine non jetable, et domaine qui reçoit du courrier (MX). */
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
            Record[] records = new Lookup(domain, Type.MX).run();
            return records != null && records.length > 0;
        } catch (Exception _) {
            return false;
        }
    }
}
