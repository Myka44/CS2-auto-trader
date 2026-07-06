package org.example.service;

import org.example.integration.TradingPlatform;
import org.example.model.Platform;
import org.example.model.PriceSnapshot;
import org.example.model.SkinCatalogEntry;
import org.example.model.Target;
import org.example.repository.PriceSnapshotRepository;
import org.example.repository.SkinRepository;
import org.example.repository.TargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Generalised version of the original Tasks.makeTargetsForSkins() logic.
 *
 * For each active, auto-adjust-enabled Target:
 *   1. Determine the price ceiling (Target.maxPriceUsdCents if set, otherwise
 *      the current lowest sell offer on that platform).
 *   2. Find the current highest competing public buy-order price.
 *   3. If ceiling < highestCompetitor + modifier, skip (would be unprofitable).
 *   4. Otherwise create or update our own target to outbid by `modifier` cents.
 */
public class TargetService {

    private static final Logger log = LoggerFactory.getLogger(TargetService.class);

    private final TargetRepository targetRepository;
    private final SkinRepository skinRepository;
    private final PriceSnapshotRepository priceSnapshotRepository;
    private final PriceAggregator priceAggregator;

    public TargetService(TargetRepository targetRepository,
                          SkinRepository skinRepository,
                          PriceSnapshotRepository priceSnapshotRepository,
                          PriceAggregator priceAggregator) {
        this.targetRepository = targetRepository;
        this.skinRepository = skinRepository;
        this.priceSnapshotRepository = priceSnapshotRepository;
        this.priceAggregator = priceAggregator;

    }

    /** Runs one full pass over all active auto-adjust targets. Safe to call repeatedly from a scheduler. */
    public void runAdjustCycle() {
        List<Target> targets = targetRepository.findAllActiveAutoAdjust();
        log.info("Running target adjust cycle for {} targets", targets.size());
        for (Target target : targets) {
            try {
                adjustOne(target);
            } catch (Exception e) {
                log.error("Failed to adjust target {} ({}): {}", target.getId(), target.getSkinMarketHashName(), e.getMessage());
                target.setLastError(e.getMessage());
                target.setLastCheckedAt(LocalDateTime.now().toString());
                targetRepository.update(target);
            }
        }
    }

    public void adjustOne(Target target) throws Exception {
        SkinCatalogEntry skin = skinRepository.findById(target.getSkinId())
                .orElseThrow(() -> new IllegalStateException("Skin not found for target " + target.getId()));
        String marketHashName = skin.getMarketHashName(); //ar reikia

        TradingPlatform client = priceAggregator.clientFor(target.getPlatform()); //kodel mes gaunam TradingPlatform is priceAggregator
        if (!client.isConfigured()) {
            log.debug("Skipping target {} -- {} is not configured", target.getId(), target.getPlatform());
            return;
        }

        Double floatMin = target.getFloatRangeMin(); //ar reikia same as marketHashName
        Double floatMax = target.getFloatRangeMax(); //ar reikia

        if(target.isAutoCalculate()){
            log.info("calculating price for {} {} {}", marketHashName, floatMin, floatMax);
            int calculated = priceAggregator.recommendedPrice(target.getPlatform(), marketHashName, floatMin, floatMax);
            log.info("Auto calculated price for {} is {}", marketHashName, calculated);
            target.setMaxPriceUsdCents(calculated);
        }

        //target.setMaxPriceUsdCents(priceAggregator.clientFor(Platform.CSFLOAT).);

        // Get the price highest offer price of the an existing buy target
        int priceThreshold;
        if (target.getMaxPriceUsdCents() > 0) {
            priceThreshold = target.getMaxPriceUsdCents();
        } else {
            priceThreshold = client.getLowestOfferPriceCents(marketHashName, floatMin, floatMax); // Kodel vadinasi lowest offer price, o ne highest
            if (priceThreshold < 0) {
                log.debug("No sell offers found for {} on {}, skipping", marketHashName, target.getPlatform());
                return;
            }
        }

        int highestCompetitor = client.getHighestPublicTargetPriceCents(marketHashName, floatMin, floatMax);
        int modifier = target.getPriceModifierCents();

        log.info("{} [{}] lowestOffer/ceiling={} highestCompetitor={} modifier={}",
                marketHashName, target.getPlatform(), priceThreshold, highestCompetitor, modifier);

        target.setLastCheckedAt(LocalDateTime.now().toString());
        target.setLastError(null); // patikirnk kaip reguoja su null

        if (priceThreshold < highestCompetitor + modifier) { // pabandyt su pamazintu modifier jei imanoma
            log.info("Skipping {} -- ceiling {} is below competing target + modifier ({})",
                    marketHashName, priceThreshold, highestCompetitor + modifier);
            targetRepository.update(target);
            return;
        }

        int desiredPrice = Math.max(highestCompetitor, 0) + modifier; // turetu egizstuot minPrice, kad nepradet nuo 0, jei nebutu
        // Never bid above our own ceiling even if highestCompetitor + modifier exceeds it for some reason.
        desiredPrice = Math.min(desiredPrice, priceThreshold);

        if (target.getPlatformTargetId() != null && !target.getPlatformTargetId().isBlank()) {
            boolean stillExists = client.targetExists(target.getPlatformTargetId());
            //TODO: panaikint target is seno client jei jis pakeiciamas
            if (!stillExists) {
                log.info("Existing platform target {} no longer exists, recreating", target.getPlatformTargetId());
                target.setPlatformTargetId(null);
            } else if (target.getLastPriceCents() != null && target.getLastPriceCents() == desiredPrice) {
                log.info("Target for {} already at the optimal price ({}), no update needed", marketHashName, desiredPrice);
                targetRepository.update(target);
                recordSnapshot(target, desiredPrice, client);
                return;
            } else {
                client.updateTarget(target, marketHashName, desiredPrice);
                target.setLastPriceCents(desiredPrice);
                targetRepository.update(target);
                recordSnapshot(target, desiredPrice, client);
                return;
            }
        }

        // No existing platform target (or it just got cleared above) -- create one.
        target.setMaxPriceUsdCents(target.getMaxPriceUsdCents() > 0 ? target.getMaxPriceUsdCents() : priceThreshold);
        String newId = client.createTarget(target, marketHashName);
        target.setPlatformTargetId(newId);
        target.setLastPriceCents(desiredPrice);
        targetRepository.update(target);
        recordSnapshot(target, desiredPrice, client);
    }

    private void recordSnapshot(Target target, int priceCents, TradingPlatform client) {
        try {
            priceSnapshotRepository.insert(new PriceSnapshot(target.getSkinId(), target.getPlatform(), priceCents, null));
        } catch (Exception e) {
            log.warn("Failed to record price snapshot for target {}: {}", target.getId(), e.getMessage());
        }
    }

    public Target createTarget(Target target) {
        return targetRepository.insert(target);
    }

    public void updateTargetSettings(Target target) {
        targetRepository.update(target);
    }

    public void deleteTarget(Target target, boolean removeFromPlatform) {
        if (removeFromPlatform && target.getPlatformTargetId() != null) {
            try {
                priceAggregator.clientFor(target.getPlatform()).deleteTarget(target.getPlatformTargetId());
            } catch (Exception e) {
                log.warn("Failed to delete platform target {} for target {}: {}", target.getPlatformTargetId(), target.getId(), e.getMessage());
            }
        }
        targetRepository.delete(target.getId());
    }

    public List<Target> findAll() {
        return targetRepository.findAll();
    }
}
