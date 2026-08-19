package com.typenull.pingdom.consultation.application;

import java.util.Optional;

public interface GeminiIntroClient {

    Optional<String> generateIntro(String message);
}
