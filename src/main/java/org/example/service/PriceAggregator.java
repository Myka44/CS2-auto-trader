package org.example.service;

import org.example.integration.TradingPlatform;
import org.example.model.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Merges price signals across all configured platforms for a given skin.
 * TargetService and AlertService go through this rather than calling a
 * specific client directly, so adding a new platform never requires
 * touching their logic.
 */
public class PriceAggregator {

    private static final Logger log = LoggerFactory.getLogger(PriceAggregator.class);

    private final Map<Platform, TradingPlatform> clients;

    public PriceAggregator(Map<Platform, TradingPlatform> clients) {
        this.clients = clients;
    }

    public record AggregatedPrice(int lowestOfferCents, Platform lowestOfferPlatform) {}

    /** Returns the single lowest sell offer across all configured + enabled platforms, or null if none found. */
    public AggregatedPrice lowestOfferAcrossPlatforms(String marketHashName, Double floatMin, Double floatMax) {
        int lowest = -1;
        Platform lowestPlatform = null;
        for (Map.Entry<Platform, TradingPlatform> entry : clients.entrySet()) {
            TradingPlatform client = entry.getValue();
            if (!client.isConfigured()) continue;
            try {
                int price = client.getLowestOfferPriceCents(marketHashName, floatMin, floatMax);
                if (price >= 0 && (lowest == -1 || price < lowest)) {
                    lowest = price;
                    lowestPlatform = entry.getKey();
                }
            } catch (Exception e) {
                log.warn("Failed to fetch lowest offer from {} for {}: {}", entry.getKey(), marketHashName, e.getMessage());
            }
        }
        return lowest == -1 ? null : new AggregatedPrice(lowest, lowestPlatform);
    }

    /** Single-platform lowest offer lookup, used when an Alert/Target is pinned to one specific platform. */
    public int lowestOfferForPlatform(Platform platform, String marketHashName, Double floatMin, Double floatMax) throws Exception {
        TradingPlatform client = clients.get(platform);
        if (client == null) {
            throw new IllegalStateException("No client registered for platform " + platform);
        }
        return client.getLowestOfferPriceCents(marketHashName, floatMin, floatMax);
    }

    public TradingPlatform clientFor(Platform platform) {
        TradingPlatform client = clients.get(platform);
        if (client == null) {
            throw new IllegalStateException("No client registered for platform " + platform);
        }
        return client;
    }

    public static Map<Platform, TradingPlatform> mapOf(TradingPlatform... platforms) {
        Map<Platform, TradingPlatform> map = new EnumMap<>(Platform.class);
        for (TradingPlatform p : platforms) {
            map.put(p.platformId(), p);
        }
        return map;
    }
}
