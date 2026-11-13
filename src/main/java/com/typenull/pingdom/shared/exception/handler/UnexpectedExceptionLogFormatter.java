package com.typenull.pingdom.shared.exception.handler;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 예외 메시지를 읽지 않고 발생 프레임과 원인 관계만 제한된 크기로 기록합니다. */
final class UnexpectedExceptionLogFormatter {

    static final int MAX_DEPTH = 8;
    static final int MAX_NODES = 16;
    static final int MAX_FRAMES_PER_EXCEPTION = 32;
    static final int MAX_TOTAL_FRAMES = 128;
    static final int MAX_SUPPRESSED_PER_EXCEPTION = 8;
    static final int MAX_CHARACTERS = 16_384;
    static final String CHARACTER_LIMIT_MARKER = "\n[character limit]";

    private static final int MAX_METADATA_CHARACTERS = 256;

    private final StringBuilder output = new StringBuilder();
    private final Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private int nodes;
    private int frames;
    private boolean characterLimitReached;

    private UnexpectedExceptionLogFormatter() {
    }

    static String format(Throwable exception) {
        UnexpectedExceptionLogFormatter formatter = new UnexpectedExceptionLogFormatter();
        try {
            formatter.appendException(exception, 0, "root");
        } catch (RuntimeException diagnosticFailure) {
            // 사용자 정의 예외의 접근자가 실패해도 원래 API 오류 응답을 방해하지 않습니다.
            formatter.append("\n[diagnostic unavailable]");
        }
        return formatter.output.toString();
    }

    private void appendException(Throwable exception, int depth, String relation) {
        if (exception == null || characterLimitReached) {
            return;
        }
        append("\n" + "  ".repeat(depth) + relation + "=");
        if (visited.contains(exception)) {
            append("[circular reference]");
            return;
        }
        if (depth >= MAX_DEPTH) {
            append("[depth limit]");
            return;
        }
        if (nodes >= MAX_NODES) {
            append("[node limit]");
            return;
        }
        visited.add(exception);
        nodes++;
        appendMetadata(exception.getClass().getName());

        StackTraceElement[] stack = exception.getStackTrace();
        int frameIndex = 0;
        while (frameIndex < stack.length && frameIndex < MAX_FRAMES_PER_EXCEPTION
                && frames < MAX_TOTAL_FRAMES && !characterLimitReached) {
            appendFrame(stack[frameIndex++], depth);
            frames++;
        }
        if (frameIndex < stack.length) {
            append("\n  [frames truncated]");
        }

        appendException(exception.getCause(), depth + 1, "cause");
        if (characterLimitReached) {
            return;
        }
        Throwable[] suppressed = exception.getSuppressed();
        int count = Math.min(suppressed.length, MAX_SUPPRESSED_PER_EXCEPTION);
        for (int index = 0; index < count && !characterLimitReached; index++) {
            appendException(suppressed[index], depth + 1, "suppressed[" + index + "]");
        }
        if (count < suppressed.length) {
            append("\n  [suppressed truncated]");
        }
    }

    private void appendFrame(StackTraceElement frame, int depth) {
        append("\n" + "  ".repeat(depth + 1) + "at ");
        appendMetadata(frame.getClassName());
        append(".");
        appendMetadata(frame.getMethodName());
        append("(");
        if (frame.isNativeMethod()) {
            append("Native Method");
        } else if (frame.getFileName() == null) {
            append("Unknown Source");
        } else {
            appendMetadata(frame.getFileName());
            if (frame.getLineNumber() >= 0) {
                append(":" + frame.getLineNumber());
            }
        }
        append(")");
    }

    private void appendMetadata(String value) {
        int count = Math.min(value.length(), MAX_METADATA_CHARACTERS);
        StringBuilder safe = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            char character = value.charAt(index);
            safe.append(Character.isISOControl(character) ? '?' : character);
        }
        append(safe.toString());
        if (count < value.length()) {
            append("[metadata truncated]");
        }
    }

    private void append(String value) {
        if (characterLimitReached) {
            return;
        }
        int remaining = MAX_CHARACTERS - CHARACTER_LIMIT_MARKER.length() - output.length();
        if (value.length() <= remaining) {
            output.append(value);
        } else {
            output.append(value, 0, remaining);
            output.append(CHARACTER_LIMIT_MARKER);
            characterLimitReached = true;
        }
    }
}
