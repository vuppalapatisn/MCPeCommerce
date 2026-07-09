package com.example.ecomserver.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * A product we've been asked to track. Created the first time someone searches/tracks it;
 * the scheduler re-scrapes it periodically so price history accumulates over time.
 */
@Entity
@Table(name = "tracked_product", uniqueConstraints = @UniqueConstraint(columnNames = {"site", "productId"}))
public class TrackedProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String site;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(nullable = false)
    private Instant firstTrackedAt;

    @Column(nullable = false)
    private Instant lastCheckedAt;

    protected TrackedProduct() {
        // JPA
    }

    public TrackedProduct(String site, String productId, String url, String title) {
        this.site = site;
        this.productId = productId;
        this.url = url;
        this.title = title;
        this.firstTrackedAt = Instant.now();
        this.lastCheckedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getSite() { return site; }
    public String getProductId() { return productId; }
    public String getUrl() { return url; }
    public String getTitle() { return title; }
    public Instant getFirstTrackedAt() { return firstTrackedAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void touch() { this.lastCheckedAt = Instant.now(); }
}
