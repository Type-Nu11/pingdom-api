package com.typenull.pingdom.analysis.application.ai;

import java.time.LocalDate;

public record AiAnalysisPrompt(
        String systemInstruction,
        String userPrompt,
        LocalDate analysisBasisDate
) {
}
