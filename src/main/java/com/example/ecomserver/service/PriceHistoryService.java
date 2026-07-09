package com.example.ecomserver.service;

import com.example.ecomserver.model.PricePoint;
import com.example.ecomserver.model.Product;
import com.example.ecomserver.model.TrackedProduct;
import com.example.ecomserver.repository.PricePointRepository;
import com.example.ecomserver.repository.TrackedProductRepository;
import com.example.ecomserver.scraper.ScraperRegistry;
import com.example.ecomserver.scraper.SiteScraper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PriceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryService.class);

    private final TrackedProductRepository trackedProductRepository;
    private final PricePointRepository pricePointRepository;
    private final ScraperRegistry registry;

    public PriceHistoryService(TrackedProductRepository trackedProductRepository,
                                PricePointRepository pricePointRepository,
                                ScraperRegistry registry) {
        this.trackedProductRepository = trackedProductRepository;
        this.pricePointRepository = pricePointRepository;
        this.registry = registry;
    }

    /**
     * Starts tracking a product (idempotent) and records an immediate price point so
     * there's at least one data point right away, rather than waiting for the next
     * scheduled scrape.
     */
    @Transactional
    public TrackedProduct startTracking(String site, String productId, String url, String title,
                                         Double initialPrice, Double initialRating, Integer initialRatingCount) {
        TrackedProduct tracked = trackedProductRepository.findBySiteAndProductId(site, productId)
                .orElseGet(() -> trackedProductRepository.save(new TrackedProduct(site, productId, url, title)));

        if (initialPrice != null) {
            pricePointRepository.save(new PricePoint(tracked.getId(), initialPrice, initialRating, initialRatingCount));
        }
        return tracked;
    }

    /**
     * Returns recorded price history for a tracked product, oldest first.
     * Returns an empty list if the product isn't being tracked yet - call startTracking
     * (or search for it, which tracks it automatically) first.
     */
    public List<PricePoint> getHistory(String site, String productId) {
        return trackedProductRepository.findBySiteAndProductId(site, productId)
                .map(tp -> pricePointRepository.findByTrackedProductIdOrderByCapturedAtAsc(tp.getId()))
                .orElse(List.of());
    }

    public List<TrackedProduct> allTracked() {
        return trackedProductRepository.findAll();
    }

    /** Re-scrapes a single tracked product and appends a new price point if the fetch succeeds. */
    @Transactional
    public void refresh(TrackedProduct tracked) {
        Optional<SiteScraper> scraper = registry.get(tracked.getSite());
        if (scraper.isEmpty()) {
            log.warn("No scraper registered for site {}", tracked.getSite());
            return;
        }
        Optional<Product> latest = scraper.get().fetchByUrl(tracked.getUrl());
        latest.ifPresentOrElse(
                p -> {
                    if (p.price() != null) {
                        pricePointRepository.save(new PricePoint(tracked.getId(), p.price(), p.rating(), p.ratingCount()));
                    }
                    tracked.touch();
                    trackedProductRepository.save(tracked);
                },
                () -> log.warn("Refresh failed for {} ({})", tracked.getTitle(), tracked.getUrl())
        );
    }
}
