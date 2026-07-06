package org.example.integration;

import org.example.model.Platform;
import org.example.model.Target;

import java.io.IOException;
import java.util.List;

/**
 * Implemented by every platform client (DMarket, CSFloat, and any future
 * site). Keeps TargetService, AlertService, and PriceAggregator fully
 * platform-agnostic -- adding a new site only means writing one new class.
 */
public interface TradingPlatform extends IdentifiablePlatform{

    Platform platformId();

    /**
     * Lowest currently-listed sell offer price for a skin, optionally
     * filtered by float range. Returns -1 if no matching offers were found.
     */
    int getLowestOfferPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException;

    /**
     * Highest existing public buy-order ("target") price for a skin,
     * optionally filtered by float range / floatPartValue. Returns -1 if
     * there are no public targets.
     */
    int getHighestPublicTargetPriceCents(String marketHashName, Double floatMin, Double floatMax) throws IOException;

    /** All public buy-order price points for a skin (used by PriceAggregator for a merged order book). */
    List<PricePoint> getPublicTargets(String marketHashName) throws IOException;

    /** Creates a buy target on the platform. Returns the platform-assigned target/order ID. */
    String createTarget(Target target, String marketHashName) throws IOException;

    /** Updates the price (and/or quantity) of an existing target. */
    void updateTarget(Target target, String marketHashName, int newPriceCents) throws IOException;

    /** Deletes a target from the platform. */
    void deleteTarget(String platformTargetId) throws IOException;

    /** True if a platform target with this ID is still registered as active. */
    boolean targetExists(String platformTargetId) throws IOException;

    /** True if this client has usable credentials configured. */
    boolean isConfigured();

    record PricePoint(int priceCents, String floatPartValue, int quantity) {}
}
