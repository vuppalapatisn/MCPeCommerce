package com.example.ecomserver.repository;

import com.example.ecomserver.model.TrackedProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackedProductRepository extends JpaRepository<TrackedProduct, Long> {
    Optional<TrackedProduct> findBySiteAndProductId(String site, String productId);
    List<TrackedProduct> findAll();
}
