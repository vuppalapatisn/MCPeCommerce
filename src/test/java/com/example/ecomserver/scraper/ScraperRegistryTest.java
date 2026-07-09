package com.example.ecomserver.scraper;

import com.example.ecomserver.model.Product;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperRegistryTest {

    private final SiteScraper fakeAmazon = fakeScraper("amazon.in");
    private final SiteScraper fakeFlipkart = fakeScraper("flipkart");

    private final ScraperRegistry registry = new ScraperRegistry(List.of(fakeAmazon, fakeFlipkart));

    @Test
    void getReturnsScraperForKnownSite() {
        assertThat(registry.get("amazon.in")).contains(fakeAmazon);
        assertThat(registry.get("flipkart")).contains(fakeFlipkart);
    }

    @Test
    void getReturnsEmptyForUnknownSite() {
        assertThat(registry.get("shopclues")).isEmpty();
    }

    @Test
    void allReturnsEveryRegisteredScraper() {
        assertThat(registry.all()).containsExactlyInAnyOrder(fakeAmazon, fakeFlipkart);
    }

    @Test
    void supportedSitesListsAllKeys() {
        assertThat(registry.supportedSites()).containsExactlyInAnyOrder("amazon.in", "flipkart");
    }

    private static SiteScraper fakeScraper(String key) {
        return new SiteScraper() {
            @Override
            public String siteKey() { return key; }

            @Override
            public List<Product> search(String query, int maxResults) { return List.of(); }

            @Override
            public Optional<Product> fetchByUrl(String url) { return Optional.empty(); }
        };
    }
}
