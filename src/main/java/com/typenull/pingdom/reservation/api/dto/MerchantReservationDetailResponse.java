package com.typenull.pingdom.reservation.api.dto;

import java.util.List;

/** 상점주에게 허용된 예약 정보와 해당 예약의 처리 이력만 제공한다. */
public record MerchantReservationDetailResponse(ReservationResponse reservation, Long placeId, String placeName,
        List<ReservationStatusHistoryResponse> statusHistory) {}
