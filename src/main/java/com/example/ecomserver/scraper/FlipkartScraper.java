package com.example.ecomserver.scraper;

import com.example.ecomserver.model.Product;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NOTE: Flipkart uses heavily obfuscated, frequently-rotated CSS class names
 * (e.g. "_1AtVbE", "_4rR01T") that are not stable across deploys. This scraper
 * therefore leans on structural selectors (product links containing "/p/")
 * and regex text extraction rather than exact class names. Expect to need
 * occasional adjustments - inspect the live page with browser devtools if
 * results come back empty.
 */
@Component
public class FlipkartScraper implements SiteScraper {

    private static final Logger log = LoggerFactory.getLogger(FlipkartScraper.class);
    private static final Pattern PID_PATTERN = Pattern.compile("pid=([A-Z0-9]+)");

    private final ScraperHttpClient http;

    public FlipkartScraper(ScraperHttpClient http) {
        this.http = http;
    }

    @Override
    public String siteKey() {
        return "flipkart";
    }

    @Override
    public List<Product> search(String query, int maxResults) {
        List<Product> results = new ArrayList<>();
        String url = "https://www.flipkart.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Document doc = http.get(url);

            // Product links on Flipkart search results always contain "/p/" in the href.
            Elements links = doc.select("a[href*=/p/]");
            for (Element link : links) {
                if (results.size() >= maxResults) break;
                Product p = parseFromLink(link);
                if (p != null) results.add(p);
            }
        } catch (IOException e) {
            log.warn("Flipkart search failed for '{}': {}", query, e.getMessage());
        }
        return results;
    }

    @Override
    public Optional<Product> fetchByUrl(String productUrl) {
        try {
            Document doc = http.get(productUrl);
            String title = doc.select("span.B_NuCI, h1 span").text();
            if (title.isBlank()) title = doc.title();

            Double price = ScraperTextUtils.extractPrice(doc.select("div._30jeq3, div._16Jk6d").text());
            if (price == null) price = ScraperTextUtils.extractPrice(doc.text());

            Double rating = ScraperTextUtils.extractRating(doc.select("div._3LWZlK").text());
            Integer ratingCount = ScraperTextUtils.extractRatingCount(doc.select("span._2_R_DZ").text());
            String image = doc.select("img._396cs4, img._2r_T1I").attr("src");
            String productId = extractPid(productUrl);

            return Optional.of(new Product("flipkart", title, productUrl, image, price, null, rating, ratingCount, productId));
        } catch (IOException e) {
            log.warn("Flipkart fetchByUrl failed for {}: {}", productUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private Product parseFromLink(Element link) {
        try {
            String href = link.attr("href");
            String url = href.startsWith("http") ? href : "https://www.flipkart.com" + href;

            // Walk up to the containing card to get title/price/rating together.
            Element card = link.closest("div");
            String cardText = card != null ? card.text() : link.text();

            String title = !link.text().isBlank() ? link.text() : link.attr("title");
            if (title == null || title.isBlank()) return null;

            Double price = ScraperTextUtils.extractPrice(cardText);
            Double rating = ScraperTextUtils.extractRating(cardText);
            Integer ratingCount = ScraperTextUtils.extractRatingCount(cardText);
            String image = card != null ? card.select("img").attr("src") : null;
            String productId = extractPid(url);

            return new Product("flipkart", title, url, image, price, null, rating, ratingCount, productId);
        } catch (Exception e) {
            log.debug("Skipping unparsable Flipkart card: {}", e.getMessage());
            return null;
        }
    }

    private String extractPid(String url) {
        Matcher m = PID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : url;
    }
}
