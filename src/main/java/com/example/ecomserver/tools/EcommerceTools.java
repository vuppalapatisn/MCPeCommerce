package com.example.ecomserver.tools;

import com.example.ecomserver.model.PricePoint;
import com.example.ecomserver.model.Product;
import com.example.ecomserver.model.TrackedProduct;
import com.example.ecomserver.scraper.ScraperRegistry;
import com.example.ecomserver.service.PriceHistoryService;
import com.example.ecomserver.service.ProductSearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EcommerceTools {

    private final ProductSearchService searchService;
    private final PriceHistoryService historyService;
    private final ScraperRegistry registry;

    public EcommerceTools(ProductSearchService searchService,
                           PriceHistoryService historyService,
                           ScraperRegistry registry) {
        this.searchService = searchService;
        this.historyService = historyService;
        this.registry = registry;
    }

    @Tool(name = "search_indian_ecommerce",
          description = "Search for a product across Indian e-commerce sites (amazon.in, flipkart, myntra, meesho). " +
                  "Returns matching products with current price, rating, and rating count, sorted by price ascending. " +
                  "Automatically starts price tracking for every result so history builds up over time.")
    public List<Product> searchIndianEcommerce(
            @ToolParam(description = "Product to search for, e.g. 'boAt Airdopes 141'") String query,
            @ToolParam(description = "Optional: subset of sites to search - any of amazon.in, flipkart, myntra, meesho. Omit to search all.", required = false)
            List<String> sites,
            @ToolParam(description = "Max results per site (default 5, max 20)", required = false) Integer maxResultsPerSite
    ) {
        int limit = clamp(maxResultsPerSite, 5, 1, 20);
        List<Product> results = searchService.searchAcrossSites(query, sites, limit);

        // Auto-track every result so callers can immediately ask for price history later,
        // and so the scheduled job starts building history from this point forward.
        for (Product p : results) {
            if (p.productId() != null) {
                historyService.startTracking(p.site(), p.productId(), p.url(), p.title(), p.price(), p.rating(), p.ratingCount());
            }
        }
        return results;
    }

    @Tool(name = "get_product_details",
          description = "Fetch fresh price, rating, and rating count for a single product page URL from a supported site " +
                  "(amazon.in, flipkart, myntra, meesho). Also records this as a new price-history data point.")
    public Product getProductDetails(
            @ToolParam(description = "Product page URL") String url,
            @ToolParam(description = "Site key: amazon.in, flipkart, myntra, or meesho") String site
    ) {
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

    @Tool(name = "get_price_history",
          description = "Get the recorded price history for a tracked product (oldest first). A product must have been " +
                  "returned by search_indian_ecommerce or get_product_details at least once before history exists. " +
                  "History accumulates going forward from when tracking started - there is no historical backfill.")
    public List<PricePoint> getPriceHistory(
            @ToolParam(description = "Site key: amazon.in, flipkart, myntra, or meesho") String site,
            @ToolParam(description = "Product ID as returned by search/details tools (e.g. Amazon ASIN, Flipkart pid)") String productId
    ) {
        return historyService.getHistory(site, productId);
    }

    @Tool(name = "list_tracked_products",
          description = "List all products currently being tracked for price history, across all sites.")
    public List<TrackedProduct> listTrackedProducts() {
        return historyService.allTracked();
    }

    private int clamp(Integer value, int fallback, int min, int max) {
        int v = value == null ? fallback : value;
        return Math.max(min, Math.min(max, v));
    }
}
