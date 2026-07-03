package org.example.service;

import org.example.model.Alert;
import org.example.model.Platform;
import org.example.model.PriceSnapshot;
import org.example.model.SkinCatalogEntry;
import org.example.repository.AlertRepository;
import org.example.repository.PriceSnapshotRepository;
import org.example.repository.SkinRepository;
import org.example.service.PriceAggregator.AggregatedPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification-only price watches. Never places an order -- only checks the
 * current lowest matching offer against the threshold and fires a
 * notification (subject to a cooldown so the same alert doesn't spam you
 * every poll cycle while the price stays below threshold).
 */
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final SkinRepository skinRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final PriceAggregator priceAggregator;
    private final NotificationService notificationService;

    public AlertService(AlertRepository alertRepository,
                         SkinRepository skinRepository,
                         PriceSnapshotRepository priceSnapshotRepository,
                         PriceAggregator priceAggregator,
                         NotificationService notificationService) {
        this.alertRepository = alertRepository;
        this.skinRepository = skinRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.priceAggregator = priceAggregator;
        this.notificationService = notificationService;
    }

    public void runCheckCycle() {
        List<Alert> alerts = alertRepository.findAllActive();
        log.info("Running alert check cycle for {} alerts", alerts.size());
        for (Alert alert : alerts) {
            try {
                checkOne(alert);
            } catch (Exception e) {
                log.error("Failed to check alert {} ({}): {}", alert.getId(), alert.getSkinMarketHashName(), e.getMessage());
            }
        }
    }

    private void checkOne(Alert alert) throws Exception {
        SkinCatalogEntry skin = skinRepository.findById(alert.getSkinId())
                .orElseThrow(() -> new IllegalStateException("Skin not found for alert " + alert.getId()));
        String marketHashName = skin.getMarketHashName();

        Double floatMin = alert.getFloatRangeMin();
        Double floatMax = alert.getFloatRangeMax();

        int currentPrice;
        Platform matchedPlatform;

        if (alert.getPlatform() != null) {
            currentPrice = priceAggregator.lowestOfferForPlatform(alert.getPlatform(), marketHashName, floatMin, floatMax);
            matchedPlatform = alert.getPlatform();
        } else {
            AggregatedPrice agg = priceAggregator.lowestOfferAcrossPlatforms(marketHashName, floatMin, floatMax);
            if (agg == null) {
                return;
            }
            currentPrice = agg.lowestOfferCents();
            matchedPlatform = agg.lowestOfferPlatform();
        }

        if (currentPrice < 0) {
            return;
        }

        alert.setLastSeenPriceCents(currentPrice);
        priceSnapshotRepository.insert(new PriceSnapshot(alert.getSkinId(), matchedPlatform, currentPrice, null));

        boolean conditionMet = alert.getDirection() == Alert.Direction.AT_OR_BELOW
                ? currentPrice <= alert.getThresholdUsdCents()
                : currentPrice >= alert.getThresholdUsdCents();

        if (!conditionMet) {
            alertRepository.update(alert);
            return;
        }

        boolean offCooldown = alert.getTriggeredAt() == null
                || Duration.between(alert.getTriggeredAt(), LocalDateTime.now()).toMinutes() >= alert.getCooldownMinutes();

        if (offCooldown) {
            notificationService.notifyAlert(alert, currentPrice, matchedPlatform);
            alert.setTriggeredAt(LocalDateTime.now());
            log.info("Alert {} triggered for {} at {} cents on {}", alert.getId(), marketHashName, currentPrice, matchedPlatform);
        }

        alertRepository.update(alert);
    }

    public Alert createAlert(Alert alert) {
        return alertRepository.insert(alert);
    }

    public void updateAlert(Alert alert) {
        alertRepository.update(alert);
    }

    public void deleteAlert(long id) {
        alertRepository.delete(id);
    }

    public List<Alert> findAll() {
        return alertRepository.findAll();
    }
}
