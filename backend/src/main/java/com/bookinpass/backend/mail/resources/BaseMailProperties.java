package com.bookinpass.backend.mail.resources;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Données communes à tous les e-mails : destinataire, langue, nom affiché. Chaque type d'e-mail en hérite et ajoute
 * ses variables ; {@link #toHashMap()} les rend toutes au gabarit.
 */
@Getter
@SuperBuilder
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class BaseMailProperties {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .changeDefaultPropertyInclusion(s -> s.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    private MailLang lang;
    /** Nom du destinataire (« Bonjour … »). */
    private String name;
    private String destination;
    private Set<String> bccRecipients;
    /** Origine du site, posée par {@code MailService}. */
    private String siteUrl;

    @JsonIgnore
    public abstract MailType getMailType();

    /** Variables du gabarit : tous les champs (classe fille comprise), sans les valeurs nulles. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> toHashMap() {
        Map<String, Object> map = new HashMap<>(MAPPER.convertValue(this, Map.class));
        map.remove("destination");
        map.remove("bccRecipients");
        map.put("baseUrl", siteUrl == null ? "" : siteUrl.replaceAll("/+$", ""));
        map.put("lang", (lang == null ? MailLang.FR : lang).name().toLowerCase(java.util.Locale.ROOT));
        return map;
    }

    /** Lien posé une fois l'objet construit (origine du site, configuration). */
    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }
}
