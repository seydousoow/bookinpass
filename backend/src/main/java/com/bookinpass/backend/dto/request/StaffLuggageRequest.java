package com.bookinpass.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Bagage enregistré au guichet : expéditeur, destinataire, poids et prix encaissé sur place (XOF). */
public record StaffLuggageRequest(
        @JsonProperty("sender_name") @NotBlank @Size(max = 100) String senderName,
        @JsonProperty("sender_phone") @Schema(example = "+221771234567") @NotNull @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String senderPhone,
        @JsonProperty("sender_id_number") @Size(max = 50) String senderIdNumber,
        @JsonProperty("receiver_name") @NotBlank @Size(max = 100) String receiverName,
        @JsonProperty("receiver_phone") @NotNull @Pattern(regexp = "\\+[1-9][0-9]{6,14}") String receiverPhone,
        @Size(max = 300) String description,
        @JsonProperty("weight_kg") @Schema(example = "12.5") @NotNull @DecimalMin("0.1") @DecimalMax("9999.9") BigDecimal weightKg,
        @Schema(example = "2000") @NotNull @DecimalMin("0") BigDecimal price) {
}
