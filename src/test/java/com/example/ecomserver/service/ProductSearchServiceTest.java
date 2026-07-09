package com.example.ecomserver.service;

import com.example.ecomserver.model.Product;
import com.example.ecomserver.scraper.ScraperRegistry;
import com.example.ecomserver.scraper.SiteScraper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchServiceTest {

    @Test
    void combinesResultsFromAllSitesSortedByPriceAscending() {
        SiteScraper amazon = stubScraper("amazon.in", List.of(
                product("amazon.in", "Amazon Item", 999.0)
        ));
        SiteScraper flipkart = stubScraper("flipkart", List.of(
                product("flipkart", "Flipkart Cheap Item", 499.0),
                product("flipkart", "Flipkart Pricey Item", 1999.0)
        ));

        ScraperRegistry registry = new ScraperRegistry(List.of(amazon, flipkart));
        ProductSearchService service = new ProductSearchService(registry);

        List<Product> results = service.searchAcrossSites("earbuds", null, 5);

        assertThat(results).hasSize(3);
        assertThat(results).extracting(Product::price)
                .containsExactly(499.0, 999.0, 1999.0);
    }

    @Test
    void onlySearchesRequestedSitesWhenSpecified() {
        SiteScraper amazon = stubScraper("amazon.in", List.of(product("amazon.in", "Amazon Item", 999.0)));
        SiteScraper flipkart = stubScraper("flipkart", List.of(product("flipkart", "Flipkart Item", 499.0)));

        ScraperRegistry registry = new ScraperRegistry(List.of(amazon, flipkart));
        ProductSearchService service = new ProductSearchService(registry);

        List<Product> results = service.searchAcrossSites("earbuds", List.of("flipkart"), 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).site()).isEqualTo("flipkart");
    }

    @Test
    void productsWithNullPriceSortToTheEnd() {
        SiteScraper amazon = stubScraper("amazon.in", List.of(
                product("amazon.in", "No price shown", null),
                product("amazon.in", "Has price", 599.0)
        ));

        ScraperRegistry registry = new ScraperRegistry(List.of(amazon));
        ProductSearchService service = new ProductSearchService(registry);

        List<Product> results = service.searchAcrossSites("earbuds", null, 5);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).price()).isEqualTo(599.0);
        assertThat(results.get(1).price()).isNull();
    }

    @Test
    void aSiteThrowingDoesNotFailTheWholeSearch() {
        SiteScraper flaky = new SiteScraper() {
            @Override public String siteKey() { return "flaky-site"; }
            @Override public List<Product> search(String query, int maxResults) { throw new RuntimeException("boom"); }
            @Override public Optional<Product> fetchByUrl(String url) { return Optional.empty(); }
        };
        SiteScraper healthy = stubScraper("flipkart", List.of(product("flipkart", "Fine", 100.0)));

        ScraperRegistry registry = new ScraperRegistry(List.of(flaky, healthy));
        ProductSearchService service = new ProductSearchService(registry);

        List<Product> results = service.searchAcrossSites("earbuds", null, 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).site()).isEqualTo("flipkart");
    }

    private static SiteScraper stubScraper(String key, List<Product> results) {
        return new SiteScraper() {
            @Override public String siteKey() { return key; }
            @Override public List<Product> search(String query, int maxResults) { return results; }
            @Override public Optional<Product> fetchByUrl(String url) { return Optional.empty(); }
        };
    }

    private static Product product(String site, String title, Double price) {
        return new Product(site, title, "https://example.com/p", null, price, null, null, null, "id-" + title.hashCode());
    }
}
