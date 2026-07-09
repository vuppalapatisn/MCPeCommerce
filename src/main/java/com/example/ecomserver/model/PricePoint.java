package com.example.ecomserver.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "price_point", indexes = @Index(columnList = "trackedProductId, capturedAt"))
public class PricePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long trackedProductId;

    @Column(nullable = false)
    private Double price;

    private Double rating;

    private Integer ratingCount;

    @Column(nullable = false)
    private Instant capturedAt;

    protected PricePoint() {
        // JPA
    }

    public PricePoint(Long trackedProductId, Double price, Double rating, Integer ratingCount) {
        this.trackedProductId = trackedProductId;
        this.price = price;
        this.rating = rating;
        this.ratingCount = ratingCount;
        this.capturedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTrackedProductId() { return trackedProductId; }
    public Double getPrice() { return price; }
    public Double getRating() { return rating; }
    public Integer getRatingCount() { return ratingCount; }
    public Instant getCapturedAt() { return capturedAt; }
}
