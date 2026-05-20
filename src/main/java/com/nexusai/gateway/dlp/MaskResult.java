package com.nexusai.gateway.dlp;

public record MaskResult(
        String maskedText,
        boolean wasMasked,
        int occurrences
) {
    public static MaskResult clean(String text) {
        return new MaskResult(text, false, 0);
    }
}
