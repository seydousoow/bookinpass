package com.bookinpass.backend.service.booking;

import com.bookinpass.backend.config.MailProperties;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E-mails de réservation de vol : confirmation, modification (par la compagnie), annulation. Gabarits FreeMarker
 * provisoires dans {@code templates/mail/} ({@code .ftlh} : valeurs échappées en HTML automatiquement).
 *
 * <p>Envoi en arrière-plan : un e-mail en échec ne fait jamais échouer une réservation ni une annulation, il est
 * journalisé. Sans serveur SMTP ({@code MAIL_HOST} vide), rien n'est envoyé.
 */
@Slf4j
@Service
public class BookingMailer {

    private final ObjectProvider<JavaMailSender> sender;
    private final Configuration freemarker;
    private final MailProperties properties;
    private final boolean enabled;

    public BookingMailer(ObjectProvider<JavaMailSender> sender, Configuration freemarker, MailProperties properties,
                         @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.freemarker = freemarker;
        this.properties = properties;
        this.enabled = host != null && !host.isBlank();
    }

    /** Commande créée chez Duffel et paiement encaissé. */
    public void bookingConfirmed(String to, String travellerName, JsonNode order, String paidAmount) {
        Map<String, Object> model = model(travellerName, order);
        model.put("amount", paidAmount);
        send(to, "Votre réservation " + order.path("booking_reference").asString("") + " est confirmée", "booking-confirmed", model);
    }

    /** Changement d'horaire ou de vol décidé par la compagnie : le voyageur doit vérifier son itinéraire. */
    public void bookingChanged(String to, String travellerName, JsonNode order) {
        send(to, "Votre réservation " + order.path("booking_reference").asString("") + " a été modifiée", "booking-changed",
                model(travellerName, order));
    }

    /** Annulation confirmée, avec le remboursement annoncé par la compagnie. */
    public void bookingCancelled(String to, String travellerName, JsonNode order, JsonNode cancellation) {
        Map<String, Object> model = model(travellerName, order);
        String refund = cancellation.path("refund_amount").asString("");
        model.put("refund", refund.isEmpty() ? "—" : refund + " " + cancellation.path("refund_currency").asString(""));
        send(to, "Votre réservation " + order.path("booking_reference").asString("") + " est annulée", "booking-cancelled", model);
    }

    /** Données communes aux gabarits ({@code layout.ftlh}) : voyageur, PNR, vols, voyageurs, lien de gestion. */
    Map<String, Object> model(String travellerName, JsonNode order) {
        Map<String, Object> model = new HashMap<>();
        model.put("traveller", travellerName == null || travellerName.isBlank() ? "voyageur" : travellerName);
        model.put("pnr", order.path("booking_reference").asString(""));
        model.put("manageUrl", properties.frontendUrl().replaceAll("/+$", "") + "/flight/manage");
        List<Map<String, String>> segments = new ArrayList<>();
        for (JsonNode slice : order.path("slices")) {
            for (JsonNode segment : slice.path("segments")) {
                segments.add(Map.of(
                        "origin", segment.path("origin").path("iata_code").asString(""),
                        "destination", segment.path("destination").path("iata_code").asString(""),
                        // Heure locale de l'aéroport, telle que Duffel la donne : « 2026-12-10 10:15 ».
                        "departure", segment.path("departing_at").asString("").replace('T', ' ').replaceAll(":00$", ""),
                        "flight", (segment.path("marketing_carrier").path("iata_code").asString("") + " "
                                + segment.path("marketing_carrier_flight_number").asString("")).trim()));
            }
        }
        model.put("segments", segments);
        List<String> passengers = new ArrayList<>();
        for (JsonNode passenger : order.path("passengers")) {
            passengers.add((passenger.path("given_name").asString("") + " " + passenger.path("family_name").asString("")).trim());
        }
        model.put("passengers", passengers);
        return model;
    }

    /** HTML de l'e-mail, gabarit {@code templates/mail/{template}.ftlh}. */
    String render(String template, Map<String, Object> model) {
        try {
            return FreeMarkerTemplateUtils.processTemplateIntoString(freemarker.getTemplate("mail/" + template + ".ftlh"), model);
        } catch (IOException | TemplateException e) {
            throw new IllegalStateException("Mail template " + template + " could not be rendered", e);
        }
    }

    private void send(String to, String subject, String template, Map<String, Object> model) {
        if (!enabled) {
            log.info("Mail '{}' to {} not sent: no SMTP server configured (MAIL_HOST)", template, mask(to));
            return;
        }
        if (to == null || to.isBlank()) {
            log.warn("Mail '{}' not sent: no recipient", template);
            return;
        }
        Thread.ofVirtual().name("mail-" + template).start(() -> {
            try {
                String html = render(template, model);
                JavaMailSender mail = sender.getObject();
                MimeMessage message = mail.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
                helper.setFrom(properties.from());
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(html, true);
                mail.send(message);
                log.info("Mail '{}' sent to {}", template, mask(to));
            } catch (MessagingException | RuntimeException e) {
                log.error("Mail '{}' to {} could not be sent", template, mask(to), e);
            }
        });
    }

    /** {@code a***@example.com} : les journaux ne gardent pas l'adresse complète. */
    private static String mask(String email) {
        if (email == null) {
            return "?";
        }
        int at = email.indexOf('@');
        return at <= 1 ? "***" + email.substring(Math.max(at, 0)) : email.charAt(0) + "***" + email.substring(at);
    }
}
