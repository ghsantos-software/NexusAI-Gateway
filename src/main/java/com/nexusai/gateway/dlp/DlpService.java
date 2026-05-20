package com.nexusai.gateway.dlp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scans text for sensitive data and replaces it with labeled tokens.
 * Patterns are applied in order from most specific to least specific to avoid partial matches.
 */
@Slf4j
@Service
public class DlpService {

    // Credit card: 16 digits in groups of 4 (Visa, Mastercard, etc)
    private static final Pattern CREDIT_CARD = Pattern.compile(
            "\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b"
    );

    // Brazilian CPF in formatted form only: 123.456.789-00
    private static final Pattern CPF = Pattern.compile(
            "\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}"
    );

    // Standard email addresses
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}",
            Pattern.CASE_INSENSITIVE
    );

    // Brazilian phone numbers with optional area code
    // Handles: (11) 99999-9999 | 11 99999 9999 | 11999999999 | +55 11 99999-9999
    private static final Pattern PHONE = Pattern.compile(
            "(?:\\+?55[\\s\\-]?)?(?:\\(?[1-9]{2}\\)?[\\s\\-]?)?(?:9[1-9]\\d{3}|[2-9]\\d{3})[\\s\\-]?\\d{4}"
    );

    public MaskResult mask(String text) {
        if (text == null || text.isBlank()) {
            return MaskResult.clean(text);
        }

        String result = text;
        int total = 0;

        // Order matters: more specific patterns first to avoid partial matches
        var cards = apply(result, CREDIT_CARD, "[CARD_REDACTED]");
        result = cards.text();
        total += cards.count();

        var cpfs = apply(result, CPF, "[CPF_REDACTED]");
        result = cpfs.text();
        total += cpfs.count();

        var emails = apply(result, EMAIL, "[EMAIL_REDACTED]");
        result = emails.text();
        total += emails.count();

        var phones = apply(result, PHONE, "[PHONE_REDACTED]");
        result = phones.text();
        total += phones.count();

        if (total > 0) {
            log.debug("DLP masked {} sensitive occurrence(s) in prompt", total);
        }

        return new MaskResult(result, total > 0, total);
    }

    private record Applied(String text, int count) {}

    private Applied apply(String text, Pattern pattern, String mask) {
        Matcher matcher = pattern.matcher(text);
        int count = 0;
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(mask));
            count++;
        }
        matcher.appendTail(sb);
        return new Applied(sb.toString(), count);
    }
}
