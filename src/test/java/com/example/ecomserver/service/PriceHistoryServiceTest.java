package com.example.ecomserver.service;

import com.example.ecomserver.model.PricePoint;
import com.example.ecomserver.model.Product;
import com.example.ecomserver.model.TrackedProduct;
import com.example.ecomserver.repository.PricePointRepository;
import com.example.ecomserver.repository.TrackedProductRepository;
import com.example.ecomserver.scraper.ScraperRegistry;
import com.example.ecomserver.scraper.SiteScraper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceHistoryServiceTest {

    @Mock private TrackedProductRepository trackedProductRepository;
    @Mock private PricePointRepository pricePointRepository;
    @Mock private ScraperRegistry registry;
    @Mock private SiteScraper scraper;

    @Test
    void startTrackingCreatesNewTrackedProductWhenNotSeenBefore() {
        when(trackedProductRepository.findBySiteAndProductId("amazon.in", "B0C1234567"))
                .thenReturn(Optional.empty());
        TrackedProduct saved = new TrackedProduct("amazon.in", "B0C1234567", "https://amazon.in/dp/B0C1234567", "Earbuds");
        when(trackedProductRepository.save(any(TrackedProduct.class))).thenReturn(saved);

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        TrackedProduct result = service.startTracking("amazon.in", "B0C1234567",
                "https://amazon.in/dp/B0C1234567", "Earbuds", 999.0, 4.2, 1000);

        assertThat(result).isEqualTo(saved);
        verify(trackedProductRepository).save(any(TrackedProduct.class));
        verify(pricePointRepository).save(any(PricePoint.class));
    }

    @Test
    void startTrackingReusesExistingTrackedProductWithoutDuplicating() {
        TrackedProduct existing = new TrackedProduct("amazon.in", "B0C1234567", "https://amazon.in/dp/B0C1234567", "Earbuds");
        when(trackedProductRepository.findBySiteAndProductId("amazon.in", "B0C1234567"))
                .thenReturn(Optional.of(existing));

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        TrackedProduct result = service.startTracking("amazon.in", "B0C1234567",
                "https://amazon.in/dp/B0C1234567", "Earbuds", 899.0, 4.2, 1000);

        assertThat(result).isEqualTo(existing);
        verify(trackedProductRepository, never()).save(any());
        verify(pricePointRepository).save(any(PricePoint.class));
    }

    @Test
    void startTrackingSkipsPricePointWhenNoInitialPriceGiven() {
        TrackedProduct existing = new TrackedProduct("amazon.in", "B0C1234567", "https://amazon.in/dp/B0C1234567", "Earbuds");
        when(trackedProductRepository.findBySiteAndProductId("amazon.in", "B0C1234567"))
                .thenReturn(Optional.of(existing));

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        service.startTracking("amazon.in", "B0C1234567", "https://amazon.in/dp/B0C1234567", "Earbuds", null, null, null);

        verify(pricePointRepository, never()).save(any());
    }

    @Test
    void getHistoryReturnsEmptyListWhenProductNeverTracked() {
        when(trackedProductRepository.findBySiteAndProductId("amazon.in", "unknown"))
                .thenReturn(Optional.empty());

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        List<PricePoint> history = service.getHistory("amazon.in", "unknown");

        assertThat(history).isEmpty();
        verifyNoInteractions(pricePointRepository);
    }

    @Test
    void getHistoryReturnsPointsOrderedOldestFirst() {
        TrackedProduct tracked = new TrackedProduct("amazon.in", "B0C1234567", "https://amazon.in/dp/B0C1234567", "Earbuds");
        // Simulate a saved entity would normally have an id; we just verify the repository call chain.
        when(trackedProductRepository.findBySiteAndProductId("amazon.in", "B0C1234567"))
                .thenReturn(Optional.of(tracked));
        List<PricePoint> points = List.of(
                new PricePoint(1L, 999.0, 4.2, 100),
                new PricePoint(1L, 949.0, 4.2, 120)
        );
        when(pricePointRepository.findByTrackedProductIdOrderByCapturedAtAsc(tracked.getId()))
                .thenReturn(points);

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        List<PricePoint> history = service.getHistory("amazon.in", "B0C1234567");

        assertThat(history).isEqualTo(points);
    }

    @Test
    void refreshAppendsNewPricePointWhenScraperReturnsData() {
        TrackedProduct tracked = new TrackedProduct("amazon.in", "B0C1234567", "https://amazon.in/dp/B0C1234567", "Earbuds");
        when(registry.get("amazon.in")).thenReturn(Optional.of(scraper));
        Product fresh = new Product("amazon.in", "Earbuds", tracked.getUrl(), null, 899.0, null, 4.3, 1050, "B0C1234567");
        when(scraper.fetchByUrl(tracked.getUrl())).thenReturn(Optional.of(fresh));

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        service.refresh(tracked);

        ArgumentCaptor<PricePoint> captor = ArgumentCaptor.forClass(PricePoint.class);
        verify(pricePointRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualTo(899.0);
        assertThat(captor.getValue().getRating()).isEqualTo(4.3);
        verify(trackedProductRepository).save(tracked);
    }

    @Test
    void refreshDoesNothingWhenNoScraperRegisteredForSite() {
        TrackedProduct tracked = new TrackedProduct("shopclues", "id1", "https://shopclues.com/p/1", "Something");
        when(registry.get("shopclues")).thenReturn(Optional.empty());

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        service.refresh(tracked);

        verifyNoInteractions(pricePointRepository);
        verify(trackedProductRepository, never()).save(any());
    }

    @Test
    void refreshSkipsSavingWhenFetchFails() {
        TrackedProduct tracked = new TrackedProduct("amazon.in", "B0C1234567", "https://amazon.in/dp/B0C1234567", "Earbuds");
        when(registry.get("amazon.in")).thenReturn(Optional.of(scraper));
        when(scraper.fetchByUrl(tracked.getUrl())).thenReturn(Optional.empty());

        PriceHistoryService service = new PriceHistoryService(trackedProductRepository, pricePointRepository, registry);
        service.refresh(tracked);

        verifyNoInteractions(pricePointRepository);
        verify(trackedProductRepository, never()).save(any());
    }
}
