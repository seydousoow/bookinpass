package com.bookinpass.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Mise en vente (publiée), retour en brouillon, ou retrait de la vente (archivée).
 */
public record StaffActivityStatusRequest(@Schema(example = "published") @NotNull @Pattern(regexp = "draft|published|archived") String status) {
}
