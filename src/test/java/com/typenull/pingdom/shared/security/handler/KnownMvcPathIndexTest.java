package com.typenull.pingdom.shared.security.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.http.server.PathContainer;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

class KnownMvcPathIndexTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesFullScanResultsAcrossPathShapes(boolean parsedPatterns) throws Exception {
        RequestMappingHandlerMapping mapping = mapping(options(parsedPatterns, true),
                "/places/{placeId}", "/admin/**", "/assets/*.json", "/{kind}/shared", "/",
                "/reports/{id:[0-9]+}", "/Ünicode/view", "/q%75ery/value");
        KnownMvcPathIndex index = initializedIndex(mapping);

        for (String value : List.of("/places/42", "/places/42/", "/places", "/p%6caces/42",
                "/places;version=1/42", "/places/42;version=1", "/places/a%2Fb", "/PLACES/42",
                "/admin", "/admin/deep/path", "/assets/config.json", "/assets/config.txt",
                "/any/shared", "/Ünicode/view", "/q%75ery/value", "/query/value", "/reports/42",
                "/reports/not-a-number", "/", "", "//places/42", "/missing/deep")) {
            PathContainer path = PathContainer.parsePath(value);

            assertThat(index.matches(path)).as("parsed=%s, path=%s", parsedPatterns, value)
                    .isEqualTo(fullScanMatches(mapping, path));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void preservesCaseInsensitiveAndUnicodeMatching(boolean parsedPatterns) throws Exception {
        RequestMappingHandlerMapping mapping = mapping(options(parsedPatterns, false),
                "/Places/{id}", "/I/{id}", "/Ünicode/view");
        KnownMvcPathIndex index = initializedIndex(mapping);

        for (String value : List.of("/PLACES/42", "/places/42", "/p%6caces/42", "/ı/42",
                "/İ/42", "/i/42", "/ünicode/view")) {
            PathContainer path = PathContainer.parsePath(value);

            assertThat(index.matches(path)).as("parsed=%s, path=%s", parsedPatterns, value)
                    .isEqualTo(fullScanMatches(mapping, path));
        }
    }

    @Test
    void keepsUnclassifiedPatternsAsCommonCandidates() throws Exception {
        RequestMappingHandlerMapping mapping = mapping(options(true, true),
                "/p*/item", "/{prefix}/shared", "/{*remaining}");
        KnownMvcPathIndex index = initializedIndex(mapping);

        for (String value : List.of("/places/item", "/other/shared", "/other/deep/path", "/")) {
            PathContainer path = PathContainer.parsePath(value);

            assertThat(index.matches(path)).isEqualTo(fullScanMatches(mapping, path));
            assertThat(index.candidatePatternCount(path)).isEqualTo(3);
        }
    }

    @Test
    void narrowsParsedCandidatesWithoutRetainingRequestPaths() throws Exception {
        String[] paths = new String[101];
        for (int i = 0; i < 100; i++) {
            paths[i] = "/group" + i + "/item";
        }
        paths[100] = "/{group}/shared";
        KnownMvcPathIndex index = initializedIndex(mapping(options(true, true), paths));

        assertThat(index.registeredPatternCount()).isEqualTo(101);
        assertThat(index.candidatePatternCount(PathContainer.parsePath("/group42/missing"))).isEqualTo(2);
        assertThat(index.candidatePatternCount(PathContainer.parsePath("/unknown/missing"))).isEqualTo(1);
        assertThat(index.matches(PathContainer.parsePath("/group42/item"))).isTrue();
        for (int i = 0; i < 1_000; i++) {
            assertThat(index.matches(PathContainer.parsePath("/unknown" + i + "/missing"))).isFalse();
        }
        assertThat(index.registeredPatternCount()).isEqualTo(101);
    }

    @Test
    void resolvesMappingOnlyAfterInitializationAndReadsMetadataOnce() throws Exception {
        RequestMappingHandlerMapping mapping = mockMapping();
        AtomicInteger resolutions = new AtomicInteger();
        KnownMvcPathIndex index = new KnownMvcPathIndex(() -> {
            resolutions.incrementAndGet();
            return mapping;
        });

        assertThat(resolutions).hasValue(0);
        assertThatIllegalStateException().isThrownBy(() -> index.matches(PathContainer.parsePath("/places/42")));
        index.afterSingletonsInstantiated();
        index.afterSingletonsInstantiated();
        for (int i = 0; i < 100; i++) {
            assertThat(index.matches(PathContainer.parsePath("/places/" + i))).isTrue();
        }

        assertThat(resolutions).hasValue(1);
        verify(mapping, times(1)).getHandlerMethods();
    }

    @Test
    void initializesSnapshotAfterRegularSingletonCreation() throws Exception {
        RequestMappingHandlerMapping mapping = mockMapping();
        AtomicBoolean mappingCreated = new AtomicBoolean();
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(KnownMvcPathIndex.class, () -> new KnownMvcPathIndex(() -> {
                assertThat(mappingCreated).isTrue();
                return context.getBean(RequestMappingHandlerMapping.class);
            }));
            context.registerBean("indexConsumer", Object.class, () -> {
                context.getBean(KnownMvcPathIndex.class);
                assertThat(mappingCreated).isFalse();
                return new Object();
            });
            context.registerBean(RequestMappingHandlerMapping.class, () -> {
                mappingCreated.set(true);
                return mapping;
            });

            context.refresh();

            assertThat(context.getBean(KnownMvcPathIndex.class).matches(PathContainer.parsePath("/places/42")))
                    .isTrue();
            verify(mapping, times(1)).getHandlerMethods();
        }
    }

    private KnownMvcPathIndex initializedIndex(RequestMappingHandlerMapping mapping) {
        KnownMvcPathIndex index = new KnownMvcPathIndex(() -> mapping);
        index.afterSingletonsInstantiated();
        return index;
    }

    private RequestMappingHandlerMapping mapping(
            RequestMappingInfo.BuilderConfiguration options, String... paths
    ) throws Exception {
        RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
        mapping.registerMapping(RequestMappingInfo.paths(paths)
                        .methods(RequestMethod.POST).consumes("application/json").params("required")
                        .options(options).build(),
                this, getClass().getDeclaredMethod("mappedEndpoint"));
        return mapping;
    }

    private RequestMappingHandlerMapping mockMapping() throws Exception {
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        RequestMappingInfo info = RequestMappingInfo.paths("/places/{id}").build();
        HandlerMethod handler = new HandlerMethod(this, getClass().getDeclaredMethod("mappedEndpoint"));
        when(mapping.getHandlerMethods()).thenReturn(Map.of(info, handler));
        return mapping;
    }

    private RequestMappingInfo.BuilderConfiguration options(boolean parsedPatterns, boolean caseSensitive) {
        RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
        if (parsedPatterns) {
            PathPatternParser parser = new PathPatternParser();
            parser.setCaseSensitive(caseSensitive);
            options.setPatternParser(parser);
        } else {
            options.setPatternParser(null);
            AntPathMatcher matcher = new AntPathMatcher();
            matcher.setCaseSensitive(caseSensitive);
            options.setPathMatcher(matcher);
        }
        return options;
    }

    private boolean fullScanMatches(RequestMappingHandlerMapping mapping, PathContainer path) {
        return mapping.getHandlerMethods().keySet().stream().anyMatch(info -> {
            if (info.getPathPatternsCondition() != null) {
                return info.getPathPatternsCondition().getPatterns().stream().anyMatch(pattern -> pattern.matches(path));
            }
            return info.getPatternsCondition() != null
                    && !info.getPatternsCondition().getMatchingPatterns(path.value()).isEmpty();
        });
    }

    private void mappedEndpoint() {
    }
}
