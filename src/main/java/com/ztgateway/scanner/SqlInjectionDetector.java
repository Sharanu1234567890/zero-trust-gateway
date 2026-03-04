package com.ztgateway.scanner;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects SQL injection patterns in request payloads.
 */
@Component
public class SqlInjectionDetector {

    private static final List<Pattern> SQL_PATTERNS = List.of(
            // Classic SQL injection
            Pattern.compile("('|\")?\\s*;\\s*(DROP|ALTER|DELETE|INSERT|UPDATE|CREATE|TRUNCATE)\\s",
                    Pattern.CASE_INSENSITIVE),
            // Union-based injection
            Pattern.compile("UNION\\s+(ALL\\s+)?SELECT\\s", Pattern.CASE_INSENSITIVE),
            // Comment-based injection
            Pattern.compile("('|\")?\\s*(--)\\s*", Pattern.CASE_INSENSITIVE),
            // Tautology-based injection
            Pattern.compile("('|\")?\\s*OR\\s+(1\\s*=\\s*1|'[^']*'\\s*=\\s*'[^']*'|true)",
                    Pattern.CASE_INSENSITIVE),
            // Stacked queries
            Pattern.compile(";\\s*(EXEC|EXECUTE|xp_|sp_)\\s", Pattern.CASE_INSENSITIVE),
            // WAITFOR-based blind injection
            Pattern.compile("WAITFOR\\s+DELAY\\s", Pattern.CASE_INSENSITIVE),
            // Batch separator
            Pattern.compile(";\\s*GO\\s*$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE),
            // MySQL-specific
            Pattern.compile("SLEEP\\s*\\(\\s*\\d+\\s*\\)", Pattern.CASE_INSENSITIVE),
            // Information schema probing
            Pattern.compile("INFORMATION_SCHEMA\\.", Pattern.CASE_INSENSITIVE),
            // LOAD_FILE / INTO OUTFILE
            Pattern.compile("(LOAD_FILE|INTO\\s+(OUT|DUMP)FILE)\\s", Pattern.CASE_INSENSITIVE),
            // Hex-encoded injection
            Pattern.compile("0x[0-9a-fA-F]{6,}"),
            // Benchmark-based blind injection
            Pattern.compile("BENCHMARK\\s*\\(", Pattern.CASE_INSENSITIVE)
    );

    public record ScanResult(boolean detected, String pattern) {
        public static ScanResult clean() { return new ScanResult(false, null); }
        public static ScanResult found(String pattern) { return new ScanResult(true, pattern); }
    }

    /**
     * Scan a string for SQL injection patterns.
     */
    public ScanResult scan(String input) {
        if (input == null || input.isBlank()) {
            return ScanResult.clean();
        }
        for (Pattern pattern : SQL_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return ScanResult.found(pattern.pattern());
            }
        }
        return ScanResult.clean();
    }
}
