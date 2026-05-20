package com.nexusai.gateway.dlp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DlpServiceTest {

    private DlpService dlpService;

    @BeforeEach
    void setUp() {
        dlpService = new DlpService();
    }

    // ─── CPF ─────────────────────────────────────────────────────────────────

    @Test
    void mask_cpf_isRedacted() {
        var result = dlpService.mask("Customer CPF is 123.456.789-00, please process.");
        assertThat(result.maskedText()).contains("[CPF_REDACTED]");
        assertThat(result.maskedText()).doesNotContain("123.456.789-00");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.occurrences()).isEqualTo(1);
    }

    @Test
    void mask_multipleCpfs_allRedacted() {
        var result = dlpService.mask("CPF 111.111.111-11 and 222.222.222-22 are both listed.");
        assertThat(result.occurrences()).isEqualTo(2);
        assertThat(result.maskedText()).doesNotContain("111.111.111-11");
        assertThat(result.maskedText()).doesNotContain("222.222.222-22");
    }

    // ─── Email ───────────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "Contact me at joao@empresa.com",
            "Send to ADMIN@CORP.COM.BR",
            "Reply to support+tag@mail.example.org"
    })
    void mask_email_isRedacted(String input) {
        var result = dlpService.mask(input);
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.maskedText()).contains("[EMAIL_REDACTED]");
    }

    // ─── Phone ───────────────────────────────────────────────────────────────

    @Test
    void mask_brazilianMobilePhone_isRedacted() {
        var result = dlpService.mask("Call me at (11) 98765-4321.");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.maskedText()).contains("[PHONE_REDACTED]");
        assertThat(result.maskedText()).doesNotContain("98765-4321");
    }

    // ─── Credit Card ─────────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "card 4111 1111 1111 1111",
            "card 4111-1111-1111-1111",
            "card 4111111111111111"
    })
    void mask_creditCard_isRedacted(String input) {
        var result = dlpService.mask(input);
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.maskedText()).contains("[CARD_REDACTED]");
    }

    // ─── Mixed ───────────────────────────────────────────────────────────────

    @Test
    void mask_multipleTypesInOneMessage_allRedacted() {
        String prompt = """
                Client: João Silva
                CPF: 987.654.321-00
                Email: joao@company.com
                Phone: (21) 91234-5678
                Card: 5500 0000 0000 0004
                Please update the profile.
                """;

        var result = dlpService.mask(prompt);
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.occurrences()).isEqualTo(4);
        assertThat(result.maskedText()).doesNotContain("987.654.321-00");
        assertThat(result.maskedText()).doesNotContain("joao@company.com");
        assertThat(result.maskedText()).doesNotContain("91234-5678");
        assertThat(result.maskedText()).doesNotContain("5500 0000 0000 0004");
        // Non-sensitive data preserved
        assertThat(result.maskedText()).contains("João Silva");
        assertThat(result.maskedText()).contains("Please update the profile");
    }

    // ─── Clean Input ─────────────────────────────────────────────────────────

    @Test
    void mask_noSensitiveData_returnedUnchanged() {
        String clean = "What is the best way to implement a REST API?";
        var result = dlpService.mask(clean);
        assertThat(result.maskedText()).isEqualTo(clean);
        assertThat(result.wasMasked()).isFalse();
        assertThat(result.occurrences()).isZero();
    }

    @Test
    void mask_nullInput_returnsNullSafely() {
        var result = dlpService.mask(null);
        assertThat(result.maskedText()).isNull();
        assertThat(result.wasMasked()).isFalse();
    }

    @Test
    void mask_blankInput_returnsUnchanged() {
        var result = dlpService.mask("   ");
        assertThat(result.wasMasked()).isFalse();
    }
}
