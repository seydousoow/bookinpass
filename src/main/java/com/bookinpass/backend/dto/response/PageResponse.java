package com.bookinpass.backend.dto.response;

import java.util.List;

/**
 * Une page de résultats.
 *
 * @param page  numéro de la page, à partir de 0
 * @param total nombre de résultats sur toutes les pages
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {
}
