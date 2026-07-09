package com.example.ecomserver.web;

import com.example.ecomserver.model.PricePoint;
import com.example.ecomserver.model.Product;
import com.example.ecomserver.model.TrackedProduct;
import com.example.ecomserver.scraper.ScraperRegistry;
import com.example.ecomserver.service.PriceHistoryService;
import com.example.ecomserver.service.ProductSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST facade over the same capabilities exposed as MCP tools ({@code EcommerceTools}),
 * so they can be explored and driven from Swagger UI or any HTTP client.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "E-commerce", description = "Search Indian e-commerce sites, read prices/ratings, and view tracked price history")
public class EcommerceRestController {

    private final ProductSearchService searchService;
    private final PriceHistoryService historyService;
    private final ScraperRegistry registry;

    public EcommerceRestController(ProductSearchService searchService,
                                   PriceHistoryService historyService,
                                   ScraperRegistry registry) {
        this.searchService = searchService;
        this.historyService = historyService;
        this.registry = registry;
    }

    @GetMapping("/sites")
    @Operation(summary = "List supported sites",
            description = "Returns the site keys this server can search and scrape.")
    public List<String> supportedSites() {
        return registry.supportedSites();
    }

    @GetMapping("/search")
    @Operation(summary = "Search products across Indian e-commerce sites",
            description = "Searches amazon.in, flipkart, myntra and meesho, returning matches with current price, "
                    + "rating and rating count, sorted by price ascending. Every result is auto-tracked so price "
                    + "history builds up over time.")
    public List<Product> search(
            @Parameter(description = "Product to search for, e.g. 'boAt Airdopes 141'", example = "boAt Airdopes 141")
            @RequestParam String query,
            @Parameter(description = "Optional subset of sites (amazon.in, flipkart, myntra, meesho). Omit to search all.")
            @RequestParam(required = false) List<String> sites,
            @Parameter(description = "Max results per site (default 5, max 20)")
            @RequestParam(required = false) Integer maxResultsPerSite) {
        int limit = clamp(maxResultsPerSite, 5, 1, 20);
        List<Product> results = searchService.searchAcrossSites(query, sites, limit);
        for (Product p : results) {
            if (p.productId() != null) {
                historyService.startTracking(p.site(), p.productId(), p.url(), p.title(), p.price(), p.rating(), p.ratingCount());
            }
        }
        return results;
    }

    @GetMapping("/product")
    @Operation(summary = "Fetch fresh details for a single product URL",
            description = "Scrapes current price, rating and rating count from a product page on a supported site, "
                    + "and records a new price-history data point.")
    public Product productDetails(
            @Parameter(description = "Product page URL") @RequestParam String url,
            @Parameter(description = "Site key: amazon.in, flipkart, myntra, or meesho", example = "amazon.in")
            @RequestParam String site) {
        var scraper = registry.get(site);
        if (scraper.isEmpty()) {
            throw new IllegalArgumentException("Unsupported site '" + site + "'. Supported: " + registry.supportedSites());
        }
        Optional<Product> product = scraper.get().fetchByUrl(url);
        if (product.isEmpty()) {
            throw new IllegalStateException("Could not fetch product details from " + url +
                    ". The page may require JavaScript rendering or the site may be blocking automated requests.");
        }
        Product p = product.get();
        if (p.productId() != null) {
            historyService.startTracking(p.site(), p.productId(), p.url(), p.title(), p.price(), p.rating(), p.ratingCount());
        }
        return p;
    }

    @GetMapping("/price-history/{site}/{productId}")
    @Operation(summary = "Get recorded price history for a tracked product",
            description = "Returns price points oldest-first. A product must have been searched or fetched at least "
                    + "once before history exists; there is no historical backfill.")
    public List<PricePoint> priceHistory(
            @Parameter(description = "Site key: amazon.in, flipkart, myntra, or meesho", example = "amazon.in")
            @PathVariable String site,
            @Parameter(description = "Product ID as returned by search/product (e.g. Amazon ASIN, Flipkart pid)")
            @PathVariable String productId) {
        return historyService.getHistory(site, productId);
    }

    @GetMapping("/tracked")
    @Operation(summary = "List all tracked products",
            description = "Returns every product currently being tracked for price history, across all sites.")
    public List<TrackedProduct> trackedProducts() {
        return historyService.allTracked();
    }

    private int clamp(Integer value, int fallback, int min, int max) {
        int v = value == null ? fallback : value;
        return Math.max(min, Math.min(max, v));
    }
}
