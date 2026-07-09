package com.example.ecomserver.scraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Thin wrapper around Jsoup with headers that make requests look like a normal browser.
 *
 * IMPORTANT: This alone will NOT reliably get past Amazon/Flipkart-grade anti-bot systems
 * in production - they increasingly require JS execution, rotate challenge pages, and rate-limit
 * by IP. Treat this as a starting point. For sustained/production use you'll likely need:
 *   - A headless browser (Playwright/Selenium) for JS-rendered pages
 *   - Rotating residential proxies
 *   - Backoff + retry and per-site request-rate caps
 *   - Regular selector maintenance (sites change their markup often)
 */
@Component
public class ScraperHttpClient {

    private static final Logger log = LoggerFactory.getLogger(ScraperHttpClient.class);

    @Value("${ecom.scraper.request-timeout-ms:8000}")
    private int timeoutMs;

    @Value("${ecom.scraper.user-agent}")
    private String userAgent;

    public Document get(String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .header("Accept-Language", "en-IN,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .followRedirects(true)
                .ignoreHttpErrors(true);
        Document doc = connection.get();
        log.debug("Fetched {} ({} bytes)", url, doc.html().length());
        return doc;
    }
}
