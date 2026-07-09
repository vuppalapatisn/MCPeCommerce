package com.example.ecomserver.model;

/**
 * A single product result as scraped from a site's search results page.
 */
public record Product(
        String site,          // "amazon.in" | "flipkart" | "myntra" | "meesho"
        String title,
        String url,
        String imageUrl,
        Double price,          // current price in INR, null if unavailable
        Double mrp,             // original/strikethrough price in INR, if shown
        Double rating,          // out of 5, null if not shown
        Integer ratingCount,    // number of ratings/reviews
        String productId        // site-specific id/ASIN/pid extracted from the URL, used as tracking key
) {}
