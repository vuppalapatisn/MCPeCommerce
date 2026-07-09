package com.example.ecomserver.scraper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperTextUtilsTest {

    @Test
    void extractsPriceWithRupeeSymbol() {
        assertThat(ScraperTextUtils.extractPrice("\u20B91,299 M.R.P: \u20B92,999")).isEqualTo(1299.0);
    }

    @Test
    void extractsPriceWithRsPrefix() {
        assertThat(ScraperTextUtils.extractPrice("Rs. 499 only")).isEqualTo(499.0);
    }

    @Test
    void extractsPriceWithDecimal() {
        assertThat(ScraperTextUtils.extractPrice("INR 1234.50 flat")).isEqualTo(1234.50);
    }

    @Test
    void returnsNullWhenNoPricePresent() {
        assertThat(ScraperTextUtils.extractPrice("Out of stock")).isNull();
    }

    @Test
    void returnsNullForNullInput() {
        assertThat(ScraperTextUtils.extractPrice(null)).isNull();
        assertThat(ScraperTextUtils.extractRating(null)).isNull();
        assertThat(ScraperTextUtils.extractRatingCount(null)).isNull();
    }

    @Test
    void extractsRatingOutOfFive() {
        assertThat(ScraperTextUtils.extractRating("4.3 out of 5 stars")).isEqualTo(4.3);
    }

    @Test
    void extractsBareRating() {
        assertThat(ScraperTextUtils.extractRating("4.1")).isEqualTo(4.1);
    }

    @Test
    void rejectsRatingAboveFive() {
        // "12.3" starts with digit not in [0-5], so no valid rating match expected
        assertThat(ScraperTextUtils.extractRating("Model 12.3 pro")).isNull();
    }

    @Test
    void extractsRatingCountWithCommas() {
        assertThat(ScraperTextUtils.extractRatingCount("12,345 ratings")).isEqualTo(12345);
    }

    @Test
    void extractsRatingCountReviews() {
        assertThat(ScraperTextUtils.extractRatingCount("(2,001 reviews)")).isEqualTo(2001);
    }

    @Test
    void extractsSmallRatingCountWithoutCommas() {
        assertThat(ScraperTextUtils.extractRatingCount("42 ratings")).isEqualTo(42);
    }
}
