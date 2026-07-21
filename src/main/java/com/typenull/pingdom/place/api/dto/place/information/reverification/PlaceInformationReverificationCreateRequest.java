package com.typenull.pingdom.place.api.dto.place.information.reverification;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PlaceInformationReverificationCreateRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotNull @Future LocalDateTime dueAt
) {}
