package com.ztgateway.scanner;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects Cross-Site Scripting (XSS) patterns in request payloads.
 */
@Component
public class XssDetector {

    private static final List<Pattern> XSS_PATTERNS = List.of(
            // Script tags
            Pattern.compile("<\\s*script[^>]*>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("</\\s*script\\s*>", Pattern.CASE_INSENSITIVE),
            // Event handlers
            Pattern.compile("\\bon\\w+\\s*=\\s*[\"']?[^\"']*[\"']?", Pattern.CASE_INSENSITIVE),
            // javascript: protocol
            Pattern.compile("javascript\\s*:", Pattern.CASE_INSENSITIVE),
            // vbscript: protocol
            Pattern.compile("vbscript\\s*:", Pattern.CASE_INSENSITIVE),
            // data: protocol with text/html
            Pattern.compile("data\\s*:\\s*text/html", Pattern.CASE_INSENSITIVE),
            // Expression in CSS
            Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE),
            // eval()
            Pattern.compile("\\beval\\s*\\(", Pattern.CASE_INSENSITIVE),
            // document.cookie
            Pattern.compile("document\\s*\\.\\s*cookie", Pattern.CASE_INSENSITIVE),
            // document.write
            Pattern.compile("document\\s*\\.\\s*write", Pattern.CASE_INSENSITIVE),
            // innerHTML
            Pattern.compile("\\.\\s*innerHTML\\s*=", Pattern.CASE_INSENSITIVE),
            // iframe injection
            Pattern.compile("<\\s*iframe[^>]*>", Pattern.CASE_INSENSITIVE),
            // object/embed/applet
            Pattern.compile("<\\s*(object|embed|applet)[^>]*>", Pattern.CASE_INSENSITIVE),
            // SVG onload
            Pattern.compile("<\\s*svg[^>]*\\bon\\w+", Pattern.CASE_INSENSITIVE),
            // Import statement in style
            Pattern.compile("@import\\s", Pattern.CASE_INSENSITIVE)
    );

    public record ScanResult(boolean detected, String pattern) {
        public static ScanResult clean() { return new ScanResult(false, null); }
        public static ScanResult found(String pattern) { return new ScanResult(true, pattern); }
    }

    /**
     * Scan a string for XSS patterns.
     */
    public ScanResult scan(String input) {
        if (input == null || input.isBlank()) {
            return ScanResult.clean();
        }
        for (Pattern pattern : XSS_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return ScanResult.found(pattern.pattern());
            }
        }
        return ScanResult.clean();
    }
}
