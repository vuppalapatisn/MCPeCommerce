package com.example.ecomserver.tools;

import com.example.ecomserver.model.Product;
import com.example.ecomserver.scraper.ScraperRegistry;
import com.example.ecomserver.scraper.SiteScraper;
import com.example.ecomserver.service.PriceHistoryService;
import com.example.ecomserver.service.ProductSearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcommerceToolsTest {

    @Mock private ProductSearchService searchService;
    @Mock private PriceHistoryService historyService;
    @Mock private ScraperRegistry registry;
    @Mock private SiteScraper scraper;

    @Test
    void searchClampsNullMaxResultsToDefaultOfFive() {
        when(searchService.searchAcrossSites(eq("earbuds"), isNull(), eq(5))).thenReturn(List.of());

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        tools.searchIndianEcommerce("earbuds", null, null);

        verify(searchService).searchAcrossSites("earbuds", null, 5);
    }

    @Test
    void searchClampsMaxResultsAboveTwentyDownToTwenty() {
        when(searchService.searchAcrossSites(anyString(), any(), eq(20))).thenReturn(List.of());

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        tools.searchIndianEcommerce("earbuds", null, 500);

        verify(searchService).searchAcrossSites("earbuds", null, 20);
    }

    @Test
    void searchClampsMaxResultsBelowOneUpToOne() {
        when(searchService.searchAcrossSites(anyString(), any(), eq(1))).thenReturn(List.of());

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        tools.searchIndianEcommerce("earbuds", null, -5);

        verify(searchService).searchAcrossSites("earbuds", null, 1);
    }

    @Test
    void searchAutoTracksEveryResultWithAProductId() {
        Product p1 = new Product("amazon.in", "Item 1", "https://amazon.in/dp/A1", null, 100.0, null, 4.0, 10, "A1");
        Product p2 = new Product("flipkart", "Item 2", "https://flipkart.com/p/B2", null, 200.0, null, 4.5, 20, "B2");
        when(searchService.searchAcrossSites(anyString(), any(), anyInt())).thenReturn(List.of(p1, p2));

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        List<Product> results = tools.searchIndianEcommerce("earbuds", null, 5);

        assertThat(results).containsExactly(p1, p2);
        verify(historyService).startTracking("amazon.in", "A1", p1.url(), p1.title(), 100.0, 4.0, 10);
        verify(historyService).startTracking("flipkart", "B2", p2.url(), p2.title(), 200.0, 4.5, 20);
    }

    @Test
    void searchSkipsTrackingResultsWithoutAProductId() {
        Product noId = new Product("amazon.in", "Item", "https://amazon.in/dp/x", null, 100.0, null, null, null, null);
        when(searchService.searchAcrossSites(anyString(), any(), anyInt())).thenReturn(List.of(noId));

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        tools.searchIndianEcommerce("earbuds", null, 5);

        verifyNoInteractions(historyService);
    }

    @Test
    void getProductDetailsThrowsForUnsupportedSite() {
        when(registry.get("shopclues")).thenReturn(Optional.empty());
        when(registry.supportedSites()).thenReturn(List.of("amazon.in", "flipkart"));

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);

        assertThatThrownBy(() -> tools.getProductDetails("https://shopclues.com/p/1", "shopclues"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported site");
    }

    @Test
    void getProductDetailsThrowsWhenScraperCannotFetch() {
        when(registry.get("amazon.in")).thenReturn(Optional.of(scraper));
        when(scraper.fetchByUrl(anyString())).thenReturn(Optional.empty());

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);

        assertThatThrownBy(() -> tools.getProductDetails("https://amazon.in/dp/X", "amazon.in"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not fetch");
    }

    @Test
    void getProductDetailsTracksResultOnSuccess() {
        Product product = new Product("amazon.in", "Item", "https://amazon.in/dp/X", null, 500.0, null, 4.1, 30, "X");
        when(registry.get("amazon.in")).thenReturn(Optional.of(scraper));
        when(scraper.fetchByUrl("https://amazon.in/dp/X")).thenReturn(Optional.of(product));

        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        Product result = tools.getProductDetails("https://amazon.in/dp/X", "amazon.in");

        assertThat(result).isEqualTo(product);
        verify(historyService).startTracking("amazon.in", "X", product.url(), product.title(), 500.0, 4.1, 30);
    }

    @Test
    void getPriceHistoryDelegatesToHistoryService() {
        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        tools.getPriceHistory("amazon.in", "X");

        verify(historyService).getHistory("amazon.in", "X");
    }

    @Test
    void listTrackedProductsDelegatesToHistoryService() {
        EcommerceTools tools = new EcommerceTools(searchService, historyService, registry);
        tools.listTrackedProducts();

        verify(historyService).allTracked();
    }
}
