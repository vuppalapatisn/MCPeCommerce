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
 * NOTE: Like Myntra, Meesho's storefront is a client-side rendered app, so a
 * plain HTTP GET may not reflect the fully rendered search results in production.
 * Kept here for structural completeness / easy swap-in of a headless-browser
 * implementation later.
 */
@Component
public class MeeshoScraper implements SiteScraper {

    private static final Logger log = LoggerFactory.getLogger(MeeshoScraper.class);
    private static final Pattern ID_PATTERN = Pattern.compile("/p/([a-zA-Z0-9]+)");

    private final ScraperHttpClient http;

    public MeeshoScraper(ScraperHttpClient http) {
        this.http = http;
    }

    @Override
    public String siteKey() {
        return "meesho";
    }

    @Override
    public List<Product> search(String query, int maxResults) {
        List<Product> results = new ArrayList<>();
        String url = "https://www.meesho.com/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Document doc = http.get(url);
            Elements links = doc.select("a[href*=/p/]");
            for (Element link : links) {
                if (results.size() >= maxResults) break;
                Product p = parseFromLink(link);
                if (p != null) results.add(p);
            }
        } catch (IOException e) {
            log.warn("Meesho search failed for '{}': {}", query, e.getMessage());
        }
        return results;
    }

    @Override
    public Optional<Product> fetchByUrl(String productUrl) {
        try {
            Document doc = http.get(productUrl);
            String title = doc.select("h1").text();
            if (title.isBlank()) title = doc.title();

            Double price = ScraperTextUtils.extractPrice(doc.text());
            Double rating = ScraperTextUtils.extractRating(doc.text());
            Integer ratingCount = ScraperTextUtils.extractRatingCount(doc.text());
            String image = doc.select("img").attr("src");
            String productId = extractId(productUrl);

            return Optional.of(new Product("meesho", title, productUrl, image, price, null, rating, ratingCount, productId));
        } catch (IOException e) {
            log.warn("Meesho fetchByUrl failed for {}: {}", productUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private Product parseFromLink(Element link) {
        try {
            String href = link.attr("href");
            String url = href.startsWith("http") ? href : "https://www.meesho.com" + href;

            Element card = link.closest("div");
            String cardText = card != null ? card.text() : link.text();

            String title = link.attr("title");
            if (title == null || title.isBlank()) title = cardText;
            if (title == null || title.isBlank()) return null;

            Double price = ScraperTextUtils.extractPrice(cardText);
            Double rating = ScraperTextUtils.extractRating(cardText);
            Integer ratingCount = ScraperTextUtils.extractRatingCount(cardText);
            String image = card != null ? card.select("img").attr("src") : null;
            String productId = extractId(url);

            return new Product("meesho", title, url, image, price, null, rating, ratingCount, productId);
        } catch (Exception e) {
            log.debug("Skipping unparsable Meesho card: {}", e.getMessage());
            return null;
        }
    }

    private String extractId(String url) {
        Matcher m = ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : url;
    }
}
