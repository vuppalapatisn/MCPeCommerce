package com.example.ecomserver.scraper;

import com.example.ecomserver.model.Product;

import java.util.List;
import java.util.Optional;

/**
 * Implemented once per e-commerce site. Kept deliberately narrow so a Jsoup-based
 * implementation can later be swapped for a headless-browser one (Playwright/Selenium)
 * without touching the service or tool layer.
 */
public interface SiteScraper {

    /** Unique key used in tool inputs/outputs, e.g. "amazon.in", "flipkart". */
    String siteKey();

    /** Search the site and return a best-effort list of matching products. */
    List<Product> search(String query, int maxResults);

    /** Re-fetch current price/rating for a product given its product page URL. */
    Optional<Product> fetchByUrl(String url);
}
