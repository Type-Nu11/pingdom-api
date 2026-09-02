package com.typenull.pingdom.menu.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PlaceMenuOrderRequest(@NotNull @Min(0) Integer displayOrder) {}
