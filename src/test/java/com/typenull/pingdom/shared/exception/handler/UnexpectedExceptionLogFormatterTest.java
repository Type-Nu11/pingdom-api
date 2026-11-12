package com.typenull.pingdom.shared.exception.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class UnexpectedExceptionLogFormatterTest {

    @Test
    void recordsFramesAndRelationshipsWithoutReadingAnyMessages() {
        Throwable cause = exception("cause", "authorization=private-token");
        Throwable suppressed = exception("suppressed", "email=private@example.test");
        Throwable failure = new MessageAccessFailsException(cause);
        failure.setStackTrace(new StackTraceElement[]{frame("root")});
        failure.addSuppressed(suppressed);

        String diagnostic = UnexpectedExceptionLogFormatter.format(failure);

        assertThat(diagnostic).contains("root=" + MessageAccessFailsException.class.getName(),
                        "cause=java.lang.IllegalStateException", "suppressed[0]=java.lang.IllegalStateException",
                        "example.Diagnostics.root(Diagnostics.java:41)", "example.Diagnostics.cause(",
                        "example.Diagnostics.suppressed(")
                .doesNotContain("private-token", "private@example.test");
    }

    @Test
    void detectsCyclesAcrossCauseAndSuppressedByIdentity() {
        Throwable first = exception("first", "first-secret");
        Throwable second = exception("second", "second-secret");
        first.initCause(second);
        second.addSuppressed(first);

        assertThat(UnexpectedExceptionLogFormatter.format(first))
                .contains("suppressed[0]=[circular reference]")
                .doesNotContain("first-secret", "second-secret");
    }

    @Test
    void distinguishesTheParentsOfNestedSuppressedExceptions() {
        Throwable failure = exception("root", "secret");
        Throwable cause = exception("cause", "secret");
        cause.addSuppressed(exception("suppressedByCause", "secret"));
        failure.initCause(cause);
        failure.addSuppressed(exception("suppressedByRoot", "secret"));

        assertThat(UnexpectedExceptionLogFormatter.format(failure)).contains(
                "\n  cause=java.lang.IllegalStateException\n    at example.Diagnostics.cause(",
                "\n    suppressed[0]=java.lang.IllegalStateException\n      at example.Diagnostics.suppressedByCause(",
                "\n  suppressed[0]=java.lang.IllegalStateException\n    at example.Diagnostics.suppressedByRoot(");
    }

    @Test
    void boundsCauseDepth() {
        Throwable failure = null;
        for (int index = UnexpectedExceptionLogFormatter.MAX_DEPTH; index >= 0; index--) {
            Throwable wrapper = exception("level" + index, "secret");
            if (failure != null) {
                wrapper.initCause(failure);
            }
            failure = wrapper;
        }

        assertThat(UnexpectedExceptionLogFormatter.format(failure))
                .contains("level" + (UnexpectedExceptionLogFormatter.MAX_DEPTH - 1) + "(", "[depth limit]")
                .doesNotContain("level" + UnexpectedExceptionLogFormatter.MAX_DEPTH + "(");
    }

    @Test
    void boundsVisitedNodesAcrossWideGraphs() {
        Throwable failure = exception("root", "secret");
        for (int parent = 0; parent < 8; parent++) {
            Throwable branch = exception("branch" + parent, "secret");
            for (int child = 0; child < 8; child++) {
                branch.addSuppressed(exception("child" + parent + "_" + child, "secret"));
            }
            failure.addSuppressed(branch);
        }

        assertThat(UnexpectedExceptionLogFormatter.format(failure)).contains("[node limit]");
    }

    @Test
    void boundsSuppressedExceptionsPerNode() {
        Throwable failure = exception("root", "secret");
        for (int index = 0; index <= UnexpectedExceptionLogFormatter.MAX_SUPPRESSED_PER_EXCEPTION; index++) {
            failure.addSuppressed(exception("suppressed" + index, "secret"));
        }

        assertThat(UnexpectedExceptionLogFormatter.format(failure))
                .contains("[suppressed truncated]", "suppressed7(")
                .doesNotContain("suppressed8(");
    }

    @Test
    void boundsFramesPerException() {
        Throwable failure = exception("root", "secret");
        StackTraceElement[] frames = new StackTraceElement[UnexpectedExceptionLogFormatter.MAX_FRAMES_PER_EXCEPTION + 1];
        Arrays.setAll(frames, index -> frame("frame" + index));
        failure.setStackTrace(frames);

        assertThat(UnexpectedExceptionLogFormatter.format(failure))
                .contains("[frames truncated]", "frame31(")
                .doesNotContain("frame32(");
    }

    @Test
    void boundsTotalFramesAcrossCauses() {
        Throwable failure = null;
        for (int index = 0; index < 6; index++) {
            Throwable wrapper = exception("root", "secret");
            StackTraceElement[] frames = new StackTraceElement[UnexpectedExceptionLogFormatter.MAX_FRAMES_PER_EXCEPTION];
            Arrays.fill(frames, frame("repeated"));
            wrapper.setStackTrace(frames);
            if (failure != null) {
                wrapper.initCause(failure);
            }
            failure = wrapper;
        }

        String diagnostic = UnexpectedExceptionLogFormatter.format(failure);

        assertThat(diagnostic.lines().filter(line -> line.stripLeading().startsWith("at ")).count())
                .isEqualTo(UnexpectedExceptionLogFormatter.MAX_TOTAL_FRAMES);
        assertThat(diagnostic).contains("[frames truncated]");
    }

    @Test
    void boundsCharactersEvenForOversizedFrameMetadata() {
        Throwable failure = exception("root", "secret");
        String oversized = "A".repeat(5_000);
        StackTraceElement[] frames = new StackTraceElement[UnexpectedExceptionLogFormatter.MAX_FRAMES_PER_EXCEPTION];
        Arrays.fill(frames, new StackTraceElement(oversized, oversized, oversized, 1));
        failure.setStackTrace(frames);

        String diagnostic = UnexpectedExceptionLogFormatter.format(failure);

        assertThat(diagnostic.length()).isLessThanOrEqualTo(UnexpectedExceptionLogFormatter.MAX_CHARACTERS);
        assertThat(diagnostic).endsWith(UnexpectedExceptionLogFormatter.CHARACTER_LIMIT_MARKER);
    }

    @Test
    void removesControlCharactersFromFrameMetadata() {
        Throwable failure = exception("root", "secret");
        failure.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("Example\r\nFORGED", "invoke\tvalue", "Example.java\u0000", 1)
        });

        assertThat(UnexpectedExceptionLogFormatter.format(failure))
                .contains("Example??FORGED.invoke?value(Example.java?:1)")
                .doesNotContain("\r", "\t", "\u0000", "\nFORGED");
    }

    @Test
    void keepsDiagnosticAccessorFailuresOutOfTheErrorResponsePath() {
        Throwable failure = new IllegalStateException("secret") {
            @Override
            public StackTraceElement[] getStackTrace() {
                throw new IllegalStateException("private diagnostic failure");
            }
        };

        assertThat(UnexpectedExceptionLogFormatter.format(failure))
                .contains("[diagnostic unavailable]")
                .doesNotContain("private diagnostic failure", "secret");
    }

    private static Throwable exception(String method, String message) {
        Throwable exception = new IllegalStateException(message);
        exception.setStackTrace(new StackTraceElement[]{frame(method)});
        return exception;
    }

    private static StackTraceElement frame(String method) {
        return new StackTraceElement("example.Diagnostics", method, "Diagnostics.java", 41);
    }

    private static final class MessageAccessFailsException extends RuntimeException {
        private MessageAccessFailsException(Throwable cause) {
            super(null, cause);
        }

        @Override
        public String getMessage() {
            throw new AssertionError("getMessage must not be called");
        }

        @Override
        public String getLocalizedMessage() {
            throw new AssertionError("getLocalizedMessage must not be called");
        }

        @Override
        public String toString() {
            throw new AssertionError("toString must not be called");
        }
    }
}
