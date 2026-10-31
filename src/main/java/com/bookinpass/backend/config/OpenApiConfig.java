package com.bookinpass.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentation OpenAPI 3 (springdoc) : contrat lisible sur {@code /swagger-ui.html}, JSON sur {@code /v3/api-docs}.
 * C'est ce JSON qui doit servir de contrat partagé avec le front (plan projet §8 : « le contrat avant l'écran »).
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    public static final String TAG_FLIGHTS = "Vols";
    public static final String TAG_STAYS = "Hébergements";
    public static final String TAG_REFERENCE = "Référentiels";
    public static final String TAG_PAYMENTS = "Paiements";
    public static final String TAG_TRANSPORT = "Transport";
    public static final String TAG_STAFF = "Back-office";
    public static final String TAG_ACTIVITIES = "Activités";

    @Bean
    OpenAPI bookInPassOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Book In Pass API")
                        .version("v1")
                        .description("""
                                API du front Book In Pass : recherche de vols (Duffel), détail d'offre, aéroports et taux \
                                de change. Les erreurs sortent au format RFC 7807 (`application/problem+json`) avec un \
                                `code` stable. Sans jeton Duffel, l'API répond avec des données de démo Dakar ⇄ Paris.""")
                        .license(new License().name("Propriétaire")))
                .tags(List.of(
                        new Tag().name(TAG_FLIGHTS).description("Recherche et détail d'offres, relayés depuis Duffel"),
                        new Tag().name(TAG_STAYS).description("Recherche, tarifs, devis et avis d'hébergement, relayés depuis Duffel Stays"),
                        new Tag().name(TAG_REFERENCE).description("Données de référence servies depuis PostgreSQL, en cache Redis"),
                        new Tag().name(TAG_STAFF).description("Personnel des gares : connexion (cookie de session), comptes, lignes, départs, contrôle des billets, bagages, incidents"),
                        new Tag().name(TAG_ACTIVITIES).description("Activités touristiques : recherche, disponibilités, réservation payée par Stripe ou mobile money, annulation, avis"),
                        new Tag().name(TAG_TRANSPORT).description("Billets de car : lignes Dem Dikk, départs et places, réservation payée par Stripe"),
                        new Tag().name(TAG_PAYMENTS).description("Paiement Stripe Checkout au prix de vente, puis réservation ; les données de carte ne passent jamais par ce serveur")));
    }
}
