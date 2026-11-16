package com.bookinpass.backend.config.ids;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Clé technique ({@code Integer}) exposée en identifiant public Sqids ({@link PublicIds}) : en JSON (champ d'un DTO) et
 * dans l'URL ({@code @PathVariable}, {@code @RequestParam}, via {@link PublicIdArgumentResolver}). Un identifiant
 * invalide reçu donne une 400.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonSerialize(using = PublicIdSerializer.class)
@JsonDeserialize(using = PublicIdDeserializer.class)
@Schema(type = "string", example = "Uk8bN3qLx2Tm", minLength = PublicIds.LENGTH)
public @interface PublicId {
}
