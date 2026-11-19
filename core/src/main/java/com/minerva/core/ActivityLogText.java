package com.minerva.core;

/**
 * Limits untrusted activity output to a single bounded log line.
 */
final class ActivityLogText {
    static final int MAX_LENGTH = 500;

    private ActivityLogText() {
    }

    static String bounded(String text) {
        if (text == null || text.isBlank()) {
            return "<empty>";
        }
        int end = Math.min(text.length(), MAX_LENGTH);
        if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            end--;
        }
        String singleLine = text.substring(0, end).replaceAll("[\\p{Cc}\\p{Zl}\\p{Zp}]", " ");
        return end < text.length() ? singleLine + "..." : singleLine;
    }
}
