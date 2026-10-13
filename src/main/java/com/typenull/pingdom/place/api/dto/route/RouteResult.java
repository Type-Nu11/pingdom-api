package com.typenull.pingdom.place.api.dto.route;

/** 이동수단별 경로 응답의 공통 공개 타입. */
public sealed interface RouteResult permits RouteResponse, TransitRouteResponse { }
