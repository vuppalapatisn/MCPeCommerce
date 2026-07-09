package com.example.ecomserver.service;

import com.example.ecomserver.model.Product;
import com.example.ecomserver.scraper.ScraperRegistry;
import com.example.ecomserver.scraper.SiteScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ScraperRegistry registry;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public ProductSearchService(ScraperRegistry registry) {
        this.registry = registry;
    }

    /**
     * Searches across the given sites (or all supported sites if null/empty) concurrently
     * and returns combined results sorted by price ascending (nulls last).
     */
    public List<Product> searchAcrossSites(String query, List<String> sites, int perSiteLimit) {
        List<SiteScraper> targets = (sites == null || sites.isEmpty())
                ? registry.all()
                : sites.stream()
                    .map(registry::get)
                    .flatMap(java.util.Optional::stream)
                    .toList();

        List<CompletableFuture<List<Product>>> futures = targets.stream()
                .map(scraper -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return scraper.search(query, perSiteLimit);
                    } catch (Exception e) {
                        log.warn("Search failed on {}: {}", scraper.siteKey(), e.getMessage());
                        return List.<Product>of();
                    }
                }, executor))
                .toList();

        return futures.stream()
                .flatMap(f -> f.join().stream())
                .sorted(Comparator.comparing(Product::price, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public List<String> supportedSites() {
        return registry.supportedSites();
    }
}
