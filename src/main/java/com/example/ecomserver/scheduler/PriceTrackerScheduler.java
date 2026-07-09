package com.example.ecomserver.scheduler;

import com.example.ecomserver.model.TrackedProduct;
import com.example.ecomserver.service.PriceHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PriceTrackerScheduler {

    private static final Logger log = LoggerFactory.getLogger(PriceTrackerScheduler.class);

    private final PriceHistoryService priceHistoryService;

    public PriceTrackerScheduler(PriceHistoryService priceHistoryService) {
        this.priceHistoryService = priceHistoryService;
    }

    // Cron is configurable via ecom.scraper.tracking-interval-cron (default: every 6 hours).
    // Keep this interval conservative - scraping too frequently is the fastest way to get
    // your server's IP blocked by these sites.
    @Scheduled(cron = "${ecom.scraper.tracking-interval-cron:0 0 */6 * * *}")
    public void refreshAllTrackedProducts() {
        var tracked = priceHistoryService.allTracked();
        log.info("Refreshing {} tracked products", tracked.size());
        for (TrackedProduct tp : tracked) {
            try {
                priceHistoryService.refresh(tp);
            } catch (Exception e) {
                log.warn("Failed to refresh {} ({}): {}", tp.getTitle(), tp.getSite(), e.getMessage());
            }
        }
    }
}
