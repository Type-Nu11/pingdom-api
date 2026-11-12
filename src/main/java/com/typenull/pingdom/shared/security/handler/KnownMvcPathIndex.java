package com.typenull.pingdom.shared.security.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

/**
 * MVC 초기화 이후 등록 경로만 보관합니다. 요청 URI를 저장하지 않으며 최종 판정은 MVC matcher가 담당합니다.
 * 현재 정적 controller 등록을 전제로 하므로 런타임 매핑 변경을 도입할 때는 인덱스 갱신도 함께 구현해야 합니다.
 */
public final class KnownMvcPathIndex implements SmartInitializingSingleton {

    private final Supplier<RequestMappingHandlerMapping> handlerMapping;
    private volatile Snapshot snapshot;

    public KnownMvcPathIndex(Supplier<RequestMappingHandlerMapping> handlerMapping) {
        this.handlerMapping = Objects.requireNonNull(handlerMapping);
    }

    /** Security 생성 중 MVC를 조회하지 않고, 모든 일반 singleton의 초기화가 끝난 뒤 완성본을 공개합니다. */
    @Override
    public synchronized void afterSingletonsInstantiated() {
        if (snapshot == null) {
            snapshot = Snapshot.from(handlerMapping.get().getHandlerMethods().keySet());
        }
    }

    public boolean matches(PathContainer path) {
        Snapshot current = initializedSnapshot();
        String segment = firstRequestSegment(path);
        List<PathPattern> candidates = segment == null
                ? current.parsedPatterns()
                : current.byFirstSegment().getOrDefault(segment, List.of());
        if (matchesAny(candidates, path)
                || (segment != null && matchesAny(current.unindexedPatterns(), path))) {
            return true;
        }
        // Legacy 조건은 사용자 정의 PathMatcher까지 보존하기 위해 후보에서 제외하지 않습니다.
        return current.legacyConditions().stream()
                .anyMatch(condition -> !condition.getMatchingPatterns(path.value()).isEmpty());
    }

    int registeredPatternCount() {
        Snapshot current = initializedSnapshot();
        return current.parsedPatterns().size() + current.legacyConditions().stream()
                .mapToInt(condition -> condition.getPatterns().size()).sum();
    }

    int candidatePatternCount(PathContainer path) {
        Snapshot current = initializedSnapshot();
        String segment = firstRequestSegment(path);
        int parsedCount = segment == null ? current.parsedPatterns().size()
                : current.byFirstSegment().getOrDefault(segment, List.of()).size()
                + current.unindexedPatterns().size();
        return parsedCount + current.legacyConditions().stream()
                .mapToInt(condition -> condition.getPatterns().size()).sum();
    }

    private Snapshot initializedSnapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("MVC path index has not been initialized");
        }
        return current;
    }

    private static boolean matchesAny(List<PathPattern> candidates, PathContainer path) {
        return candidates.stream().anyMatch(pattern -> pattern.matches(path));
    }

    private static String firstRequestSegment(PathContainer path) {
        List<PathContainer.Element> elements = path.elements();
        if (elements.size() < 2 || !"/".equals(elements.get(0).value())
                || !(elements.get(1) instanceof PathContainer.PathSegment segment)) {
            return null;
        }
        // decoded 값으로 encoded 문자·matrix parameter를 처리합니다. 비 ASCII는 case folding 누락을 막기 위해 전체 후보로 보냅니다.
        return indexKey(segment.valueToMatch());
    }

    private static String firstPatternSegment(String pattern) {
        if (!pattern.startsWith("/")) {
            return null;
        }
        int separator = pattern.indexOf('/', 1);
        return indexKey(pattern.substring(1, separator < 0 ? pattern.length() : separator));
    }

    private static String indexKey(String segment) {
        if (segment.isEmpty()) {
            return null;
        }
        for (int i = 0; i < segment.length(); i++) {
            char value = segment.charAt(i);
            if (!((value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                    || (value >= '0' && value <= '9') || value == '-' || value == '_')) {
                return null;
            }
        }
        // case-sensitive matcher도 최종 검사를 수행하므로 소문자 key는 후보만 넓히며 잘못된 일치를 만들지 않습니다.
        return segment.toLowerCase(Locale.ROOT);
    }

    private record Snapshot(
            List<PathPattern> parsedPatterns,
            Map<String, List<PathPattern>> byFirstSegment,
            List<PathPattern> unindexedPatterns,
            List<PatternsRequestCondition> legacyConditions
    ) {

        private static Snapshot from(Iterable<RequestMappingInfo> mappings) {
            List<PathPattern> parsed = new ArrayList<>();
            Map<String, List<PathPattern>> bySegment = new HashMap<>();
            List<PathPattern> unindexed = new ArrayList<>();
            List<PatternsRequestCondition> legacy = new ArrayList<>();
            for (RequestMappingInfo mapping : mappings) {
                if (mapping.getPathPatternsCondition() != null) {
                    for (PathPattern pattern : mapping.getPathPatternsCondition().getPatterns()) {
                        parsed.add(pattern);
                        String segment = firstPatternSegment(pattern.getPatternString());
                        if (segment == null) {
                            unindexed.add(pattern);
                        } else {
                            bySegment.computeIfAbsent(segment, ignored -> new ArrayList<>()).add(pattern);
                        }
                    }
                } else if (mapping.getPatternsCondition() != null) {
                    legacy.add(mapping.getPatternsCondition());
                }
            }
            Map<String, List<PathPattern>> immutableSegments = new HashMap<>();
            bySegment.forEach((key, value) -> immutableSegments.put(key, List.copyOf(value)));
            return new Snapshot(List.copyOf(parsed), Map.copyOf(immutableSegments),
                    List.copyOf(unindexed), List.copyOf(legacy));
        }
    }
}
