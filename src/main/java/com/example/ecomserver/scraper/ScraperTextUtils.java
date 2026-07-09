package com.example.ecomserver.scraper;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Site markup for these e-commerce sites changes often and class names are frequently
 * obfuscated (especially Flipkart/Myntra). Rather than depending entirely on exact CSS
 * selectors that will silently start returning nothing after the next redesign, each
 * scraper tries a specific selector first and falls back to these regex heuristics
 * against the surrounding card's text content.
 */
public final class ScraperTextUtils {

    // e.g. 1234 / Rs. 1234 / INR 1234
    private static final Pattern PRICE_PATTERN =
            Pattern.compile("(?:\u20B9|Rs\\.?|INR)\\s?([0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]{1,2})?)");

    // "4.3 out of 5" or "4.3 stars" - requires explicit rating context
    private static final Pattern RATING_WITH_CONTEXT_PATTERN =
            Pattern.compile("([0-5](?:\\.[0-9])?)\\s*(?:out of 5|stars?)");

    // A bare rating value, e.g. "4.3" - only trusted when it's the ENTIRE (trimmed) input,
    // such as text pulled from an element already known to be a rating badge. Applying this
    // to a whole product-card text blob would false-positive on prices/model numbers
    // (e.g. matching "1" out of "12.3"), so it deliberately does NOT search within longer text.
    private static final Pattern BARE_RATING_PATTERN = Pattern.compile("^[0-5](?:\\.[0-9])?$");

    // "1,234 ratings" / "(1,234)" / "1,234 reviews"
    private static final Pattern RATING_COUNT_PATTERN =
            Pattern.compile("([0-9]{1,3}(?:,[0-9]{3})*)\\s*(?:ratings?|reviews?)");

    private ScraperTextUtils() {}

    public static Double extractPrice(String text) {
        if (text == null) return null;
        Matcher m = PRICE_PATTERN.matcher(text);
        if (m.find()) {
            return parseIndianNumber(m.group(1));
        }
        return null;
    }

    public static Double extractRating(String text) {
        if (text == null) return null;
        Matcher context = RATING_WITH_CONTEXT_PATTERN.matcher(text);
        if (context.find()) {
            return Double.parseDouble(context.group(1));
        }
        Matcher bare = BARE_RATING_PATTERN.matcher(text.trim());
        if (bare.matches()) {
            return Double.parseDouble(bare.group());
        }
        return null;
    }

    public static Integer extractRatingCount(String text) {
        if (text == null) return null;
        Matcher m = RATING_COUNT_PATTERN.matcher(text);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1).replace(",", ""));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Double parseIndianNumber(String raw) {
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String extractGroup(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : null;
    }
}
