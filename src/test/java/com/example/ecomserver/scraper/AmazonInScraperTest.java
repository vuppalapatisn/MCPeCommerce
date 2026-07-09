package com.example.ecomserver.scraper;

import com.example.ecomserver.model.Product;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests parsing logic only, against static HTML fixtures - no real network calls.
 * ScraperHttpClient is mocked so we control exactly what "page" is parsed.
 */
@ExtendWith(MockitoExtension.class)
class AmazonInScraperTest {

    @Mock
    private ScraperHttpClient httpClient;

    private static final String SEARCH_RESULTS_HTML = """
        <html><body>
          <div data-component-type="s-search-result" data-asin="B0C1234567">
            <h2><a href="/Boat-Airdopes-141/dp/B0C1234567/ref=sr_1_1"><span>boAt Airdopes 141 Bluetooth Truly Wireless Earbuds</span></a></h2>
            <img class="s-image" src="https://m.media-amazon.com/images/I/example.jpg" />
            <span class="a-price"><span class="a-offscreen">\u20B91,299</span></span>
            <span class="a-icon-alt">4.2 out of 5 stars</span>
            <span>12,543 ratings</span>
          </div>
          <div data-component-type="s-search-result" data-asin="B0C7654321">
            <h2><a href="/Boat-Rockerz-255/dp/B0C7654321/ref=sr_1_2"><span>boAt Rockerz 255 Pro+</span></a></h2>
            <img class="s-image" src="https://m.media-amazon.com/images/I/example2.jpg" />
            <span class="a-price"><span class="a-offscreen">\u20B9999</span></span>
            <span class="a-icon-alt">4.0 out of 5 stars</span>
            <span>8,210 ratings</span>
          </div>
        </body></html>
        """;

    private static final String PRODUCT_PAGE_HTML = """
        <html><body>
          <span id="productTitle">boAt Airdopes 141 Bluetooth Truly Wireless Earbuds</span>
          <div class="a-price"><span class="a-offscreen">\u20B91,299</span></div>
          <span class="a-icon-alt">4.2 out of 5 stars</span>
          <span id="acrCustomerReviewText">12,543 ratings</span>
          <img id="landingImage" src="https://m.media-amazon.com/images/I/example.jpg" />
        </body></html>
        """;

    @Test
    void searchParsesEachResultCard() throws IOException {
        when(httpClient.get(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Jsoup.parse(SEARCH_RESULTS_HTML));

        AmazonInScraper scraper = new AmazonInScraper(httpClient);
        List<Product> results = scraper.search("boat earbuds", 10);

        assertThat(results).hasSize(2);

        Product first = results.get(0);
        assertThat(first.title()).contains("Airdopes 141");
        assertThat(first.price()).isEqualTo(1299.0);
        assertThat(first.rating()).isEqualTo(4.2);
        assertThat(first.ratingCount()).isEqualTo(12543);
        assertThat(first.site()).isEqualTo("amazon.in");
        assertThat(first.productId()).isEqualTo("B0C1234567");
        assertThat(first.url()).startsWith("https://www.amazon.in");
    }

    @Test
    void searchRespectsMaxResultsLimit() throws IOException {
        when(httpClient.get(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Jsoup.parse(SEARCH_RESULTS_HTML));

        AmazonInScraper scraper = new AmazonInScraper(httpClient);
        List<Product> results = scraper.search("boat earbuds", 1);

        assertThat(results).hasSize(1);
    }

    @Test
    void searchReturnsEmptyListOnIOException() throws IOException {
        when(httpClient.get(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IOException("connection reset"));

        AmazonInScraper scraper = new AmazonInScraper(httpClient);
        List<Product> results = scraper.search("boat earbuds", 10);

        assertThat(results).isEmpty();
    }

    @Test
    void fetchByUrlParsesProductPage() throws IOException {
        String url = "https://www.amazon.in/Boat-Airdopes-141/dp/B0C1234567";
        when(httpClient.get(url)).thenReturn(Jsoup.parse(PRODUCT_PAGE_HTML));

        AmazonInScraper scraper = new AmazonInScraper(httpClient);
        Optional<Product> result = scraper.fetchByUrl(url);

        assertThat(result).isPresent();
        Product p = result.get();
        assertThat(p.title()).contains("Airdopes 141");
        assertThat(p.price()).isEqualTo(1299.0);
        assertThat(p.rating()).isEqualTo(4.2);
        assertThat(p.ratingCount()).isEqualTo(12543);
        assertThat(p.productId()).isEqualTo("B0C1234567");
    }

    @Test
    void fetchByUrlReturnsEmptyOnIOException() throws IOException {
        String url = "https://www.amazon.in/dp/B0C1234567";
        when(httpClient.get(url)).thenThrow(new IOException("timeout"));

        AmazonInScraper scraper = new AmazonInScraper(httpClient);
        assertThat(scraper.fetchByUrl(url)).isEmpty();
    }

    @Test
    void siteKeyIsAmazonIn() {
        AmazonInScraper scraper = new AmazonInScraper(httpClient);
        assertThat(scraper.siteKey()).isEqualTo("amazon.in");
    }
}
