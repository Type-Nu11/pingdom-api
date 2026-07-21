package com.typenull.pingdom.place.api.dto.place.information.reverification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceInformationReverificationResponseRequest(
        @NotBlank @Size(max = 1000) String responseNote
) {}
