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
 * NOTE: Myntra's search results are rendered client-side via JS (React) in the
 * production site, which means a plain Jsoup GET may return an near-empty shell
 * for the search page. This implementation will work for any server-rendered
 * fallback Myntra serves, but if you get empty results in practice, this is the
 * scraper most likely to need a headless-browser (Playwright) replacement -
 * see the README for how to swap SiteScraper implementations.
 */
@Component
public class MyntraScraper implements SiteScraper {

    private static final Logger log = LoggerFactory.getLogger(MyntraScraper.class);
    private static final Pattern ID_PATTERN = Pattern.compile("/(\\d+)/buy");

    private final ScraperHttpClient http;

    public MyntraScraper(ScraperHttpClient http) {
        this.http = http;
    }

    @Override
    public String siteKey() {
        return "myntra";
    }

    @Override
    public List<Product> search(String query, int maxResults) {
        List<Product> results = new ArrayList<>();
        String url = "https://www.myntra.com/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "-");
        try {
            Document doc = http.get(url);
            Elements links = doc.select("a[href*=/buy]");
            for (Element link : links) {
                if (results.size() >= maxResults) break;
                Product p = parseFromLink(link);
                if (p != null) results.add(p);
            }
        } catch (IOException e) {
            log.warn("Myntra search failed for '{}': {}", query, e.getMessage());
        }
        return results;
    }

    @Override
    public Optional<Product> fetchByUrl(String productUrl) {
        try {
            Document doc = http.get(productUrl);
            String title = doc.select("h1.pdp-title, h1.pdp-name").text();
            if (title.isBlank()) title = doc.title();

            Double price = ScraperTextUtils.extractPrice(doc.select("span.pdp-price").text());
            Double rating = ScraperTextUtils.extractRating(doc.select("div.index-overallRating").text());
            Integer ratingCount = ScraperTextUtils.extractRatingCount(doc.select("div.index-ratingsCount").text());
            String image = doc.select("picture img").attr("src");
            String productId = extractId(productUrl);

            return Optional.of(new Product("myntra", title, productUrl, image, price, null, rating, ratingCount, productId));
        } catch (IOException e) {
            log.warn("Myntra fetchByUrl failed for {}: {}", productUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private Product parseFromLink(Element link) {
        try {
            String href = link.attr("href");
            String url = href.startsWith("http") ? href : "https://www.myntra.com/" + href;

            Element card = link.closest("li, div");
            String cardText = card != null ? card.text() : link.text();

            String title = link.attr("title");
            if (title == null || title.isBlank()) title = card != null ? card.text() : link.text();
            if (title == null || title.isBlank()) return null;

            Double price = ScraperTextUtils.extractPrice(cardText);
            Double rating = ScraperTextUtils.extractRating(cardText);
            Integer ratingCount = ScraperTextUtils.extractRatingCount(cardText);
            String image = card != null ? card.select("img").attr("src") : null;
            String productId = extractId(url);

            return new Product("myntra", title, url, image, price, null, rating, ratingCount, productId);
        } catch (Exception e) {
            log.debug("Skipping unparsable Myntra card: {}", e.getMessage());
            return null;
        }
    }

    private String extractId(String url) {
        Matcher m = ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : url;
    }
}
