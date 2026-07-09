package com.example.ecomserver.repository;

import com.example.ecomserver.model.PricePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PricePointRepository extends JpaRepository<PricePoint, Long> {
    List<PricePoint> findByTrackedProductIdOrderByCapturedAtAsc(Long trackedProductId);
}
