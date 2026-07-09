package com.example.ecomserver.scraper;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ScraperRegistry {

    private final Map<String, SiteScraper> scrapersByKey;

    public ScraperRegistry(List<SiteScraper> scrapers) {
        this.scrapersByKey = scrapers.stream()
                .collect(Collectors.toMap(SiteScraper::siteKey, Function.identity()));
    }

    public Optional<SiteScraper> get(String siteKey) {
        return Optional.ofNullable(scrapersByKey.get(siteKey));
    }

    public List<SiteScraper> all() {
        return List.copyOf(scrapersByKey.values());
    }

    public List<String> supportedSites() {
        return List.copyOf(scrapersByKey.keySet());
    }
}
