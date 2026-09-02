package com.typenull.pingdom.menu.api.dto;

import com.typenull.pingdom.menu.domain.PlaceMenuStatus;
import jakarta.validation.constraints.NotNull;

public record PlaceMenuStatusRequest(@NotNull PlaceMenuStatus status) {}
