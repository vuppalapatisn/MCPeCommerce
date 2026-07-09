package com.example.ecomserver.scraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

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

    /**
     * DEV ONLY. When true, the client trusts ALL TLS certificates, bypassing validation.
     * Use this only to work around a corporate TLS-inspection proxy on a trusted network
     * for local demos; it defeats HTTPS protection against man-in-the-middle attacks and
     * must never be enabled in production. Prefer importing the corporate root CA into the
     * JVM truststore instead.
     */
    @Value("${ecom.scraper.insecure-tls:false}")
    private boolean insecureTls;

    private SSLSocketFactory trustAllSocketFactory;

    public Document get(String url) throws IOException {
        Connection connection = Jsoup.connect(url)
                .userAgent(userAgent)
                .timeout(timeoutMs)
                .header("Accept-Language", "en-IN,en;q=0.9")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .followRedirects(true)
                .ignoreHttpErrors(true);
        if (insecureTls) {
            connection.sslSocketFactory(trustAllSocketFactory());
        }
        Document doc = connection.get();
        log.debug("Fetched {} ({} bytes)", url, doc.html().length());
        return doc;
    }

    /** Lazily builds (and caches) a trust-everything SSL socket factory. Dev-only, see {@link #insecureTls}. */
    private synchronized SSLSocketFactory trustAllSocketFactory() {
        if (trustAllSocketFactory == null) {
            log.warn("INSECURE TLS is ENABLED (ecom.scraper.insecure-tls=true) - certificate validation "
                    + "is disabled for scraping. Never use this in production.");
            TrustManager[] trustAll = { new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            try {
                SSLContext ctx = SSLContext.getInstance("TLS");
                ctx.init(null, trustAll, new java.security.SecureRandom());
                trustAllSocketFactory = ctx.getSocketFactory();
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failed to build insecure SSL context", e);
            }
        }
        return trustAllSocketFactory;
    }
}
