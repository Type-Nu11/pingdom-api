package com.typenull.pingdom.product.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import com.typenull.pingdom.product.domain.ReservableProduct;
import com.typenull.pingdom.product.domain.ReservableProductStatus;

public record ReservableProductResponse(
        Long id,
        Long placeId,
        AvailabilityProductType productType,
        String name,
        ReservableProductStatus status
) {
    public static ReservableProductResponse from(ReservableProduct product) {
        return new ReservableProductResponse(product.getId(), product.getPlaceId(), product.getProductType(),
                product.getName(), product.getStatus());
    }
}
