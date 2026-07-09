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

@Component
public class AmazonInScraper implements SiteScraper {

    private static final Logger log = LoggerFactory.getLogger(AmazonInScraper.class);
    private static final Pattern ASIN_PATTERN = Pattern.compile("/(?:dp|gp/product)/([A-Z0-9]{10})");

    private final ScraperHttpClient http;

    public AmazonInScraper(ScraperHttpClient http) {
        this.http = http;
    }

    @Override
    public String siteKey() {
        return "amazon.in";
    }

    @Override
    public List<Product> search(String query, int maxResults) {
        List<Product> results = new ArrayList<>();
        String url = "https://www.amazon.in/s?k=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            Document doc = http.get(url);

            // Amazon marks each result card with data-component-type="s-search-result".
            // This has been stable for a long time but is not guaranteed.
            Elements cards = doc.select("div[data-component-type=s-search-result]");
            for (Element card : cards) {
                if (results.size() >= maxResults) break;
                Product p = parseCard(card);
                if (p != null) results.add(p);
            }
        } catch (IOException e) {
            log.warn("Amazon.in search failed for '{}': {}", query, e.getMessage());
        }
        return results;
    }

    @Override
    public Optional<Product> fetchByUrl(String productUrl) {
        try {
            Document doc = http.get(productUrl);
            String title = doc.select("#productTitle").text();
            if (title.isBlank()) title = doc.title();

            Double price = ScraperTextUtils.extractPrice(doc.select(".a-price .a-offscreen").text());
            if (price == null) price = ScraperTextUtils.extractPrice(doc.select("#corePrice_feature_div").text());

            Double rating = ScraperTextUtils.extractRating(doc.select("span.a-icon-alt").first() != null
                    ? doc.select("span.a-icon-alt").first().text() : null);
            Integer ratingCount = ScraperTextUtils.extractRatingCount(doc.select("#acrCustomerReviewText").text());

            String productId = extractAsin(productUrl);
            String image = doc.select("#landingImage").attr("src");

            return Optional.of(new Product("amazon.in", title, productUrl, image, price, null, rating, ratingCount, productId));
        } catch (IOException e) {
            log.warn("Amazon.in fetchByUrl failed for {}: {}", productUrl, e.getMessage());
            return Optional.empty();
        }
    }

    private Product parseCard(Element card) {
        try {
            Element titleEl = card.selectFirst("h2 a span, h2 span");
            Element linkEl = card.selectFirst("h2 a");
            if (titleEl == null || linkEl == null) return null;

            String title = titleEl.text();
            String href = linkEl.attr("href");
            String url = href.startsWith("http") ? href : "https://www.amazon.in" + href;

            Double price = ScraperTextUtils.extractPrice(card.select(".a-price .a-offscreen").text());
            if (price == null) price = ScraperTextUtils.extractPrice(card.text());

            Element ratingAlt = card.selectFirst("span.a-icon-alt");
            Double rating = ratingAlt != null ? ScraperTextUtils.extractRating(ratingAlt.text()) : null;

            Integer ratingCount = ScraperTextUtils.extractRatingCount(card.text());
            String image = card.select("img.s-image").attr("src");
            String productId = extractAsin(url);
            if (productId == null) {
                productId = card.attr("data-asin");
            }

            return new Product("amazon.in", title, url, image, price, null, rating, ratingCount, productId);
        } catch (Exception e) {
            log.debug("Skipping unparsable Amazon.in card: {}", e.getMessage());
            return null;
        }
    }

    private String extractAsin(String url) {
        Matcher m = ASIN_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }
}
