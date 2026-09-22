package com.typenull.pingdom.place;

import java.util.List;

/** 탐색·전환·검증 fixture의 요청 형식과 기대 결과 문구를 전달하는 값입니다. */
public record ExplorationConversionVerificationScenario(
        String name, String method, String path, int expectedStatus, List<String> assertions) {
}
