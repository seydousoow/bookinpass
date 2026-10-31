package com.bookinpass.backend.dto.duffel;

import java.util.List;

/** Corps de {@code PATCH /air/orders/{id}} : clients Duffel ({@code icu_…}) qui peuvent gérer la commande. */
public record DuffelOrderUpdate(Data data) {

    public record Data(List<String> users) {
    }
}
